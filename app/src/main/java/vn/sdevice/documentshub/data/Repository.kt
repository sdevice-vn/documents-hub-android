package vn.sdevice.documentshub.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.time.Instant

/**
 * Lưu trữ trên Google Drive của người dùng (quyền drive.file):
 *   Drive/Documents Hub/<Phân loại>/<tệp>   -> tệp thật, mở được từ app Drive hoặc máy tính
 *   mô tả (description) của tệp              -> ghi chú
 *   appProperties "pc" / "du"                -> số trang PDF / thời lượng (ms)
 *   thùng rác                                -> thùng rác của Drive
 * App chỉ nhìn thấy tệp và thư mục do chính nó tạo.
 */
class Repository(private val app: Context, private val auth: DriveAuth) {
    private val api = DriveApi(auth)
    private val prefs = app.getSharedPreferences("drive", Context.MODE_PRIVATE)
    private val loadLock = Mutex()

    private var rootId: String? = prefs.getString("rootId", null)
    private var catIds: Map<String, String> = emptyMap() // tên phân loại -> id thư mục (giữ thứ tự)

    data class Library(val categories: List<String>, val files: List<DocFile>)

    companion object {
        const val MAX_UPLOAD = 1024L * 1024 * 1024
        const val ROOT_NAME = "Documents Hub"
    }

    // ---------- Tài khoản ----------

    suspend fun signIn() { auth.token() }

    suspend fun accountEmail(): String? = api.aboutEmail()

    suspend fun clearLocal() {
        auth.forget()
        rootId = null
        catIds = emptyMap()
        prefs.edit().clear().apply()
        withContext(Dispatchers.IO) { File(app.cacheDir, "docs").deleteRecursively() }
    }

    // ---------- Tải danh sách ----------

    private suspend fun ensureRoot(): String {
        rootId?.let { id ->
            val ok = runCatching { !api.get(id, "id,trashed").optBoolean("trashed") }.getOrDefault(false)
            if (ok) return id
        }
        val found = api.list("name = '$ROOT_NAME' and mimeType = '${DriveApi.FOLDER}' and trashed = false", "id", "createdTime")
            .firstOrNull()?.getString("id")
        val id = found ?: api.createFolder(ROOT_NAME, null)
        rootId = id
        prefs.edit().putString("rootId", id).apply()
        return id
    }

    suspend fun loadLibrary(): Library = loadLock.withLock {
        val root = ensureRoot()
        val folderQuery = "'$root' in parents and mimeType = '${DriveApi.FOLDER}' and trashed = false"
        var folders = api.list(folderQuery, "id,name", "createdTime")
        if (folders.isEmpty()) {
            DEFAULT_CATEGORIES.forEach { api.createFolder(it, root) }
            folders = api.list(folderQuery, "id,name", "createdTime")
        }
        val map = LinkedHashMap<String, String>()
        folders.forEach { map[it.getString("name")] = it.getString("id") }
        catIds = map
        val byId = map.entries.associate { it.value to it.key }

        val files = api.list("mimeType != '${DriveApi.FOLDER}'", DriveApi.FILE_FIELDS)
            .mapNotNull { toDoc(it, byId) }
        Library(map.keys.toList(), files)
    }

    private fun parseTime(s: String): Long = runCatching { Instant.parse(s).toEpochMilli() }.getOrDefault(0L)

    private fun toDoc(o: JSONObject, byId: Map<String, String>): DocFile? {
        val parents = o.optJSONArray("parents") ?: return null
        var cat: String? = null
        for (i in 0 until parents.length()) {
            cat = byId[parents.getString(i)]
            if (cat != null) break
        }
        if (cat == null) return null // không nằm trong thư viện Documents Hub
        val props = o.optJSONObject("appProperties")
        val trashed = o.optBoolean("trashed")
        return DocFile(
            id = o.getString("id"),
            name = o.getString("name"),
            size = o.optString("size").toLongOrNull() ?: 0L,
            category = cat,
            notes = o.optString("description"),
            uploadDate = parseTime(o.optString("createdTime")),
            isTrash = trashed,
            trashedAt = if (trashed) parseTime(o.optString("modifiedTime")) else 0L,
            pageCount = props?.optString("pc")?.toIntOrNull() ?: 0,
            duration = props?.optString("du")?.toLongOrNull() ?: 0L,
            thumbUrl = o.optString("thumbnailLink").ifEmpty { null }
        )
    }

    // ---------- Thêm tệp ----------

    fun displayName(uri: Uri): String {
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "tep"
        app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) name = c.getString(0)
        }
        return sanitizeName(name).ifEmpty { "tep" }
    }

    private fun sizeOf(uri: Uri): Long {
        app.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) return c.getLong(0)
        }
        return -1L
    }

    private fun cacheDir() = File(app.cacheDir, "docs").apply { mkdirs() }

    suspend fun upload(uri: Uri, category: String, onProgress: (Float) -> Unit) {
        val name = displayName(uri)
        val declared = sizeOf(uri)
        if (declared > MAX_UPLOAD) throw IllegalStateException("\"$name\" lớn hơn giới hạn 1 GB")
        val catId = catIds[category] ?: catIds.values.firstOrNull()
            ?: throw IllegalStateException("Chưa tải được danh sách phân loại, hãy kéo xuống để làm mới")
        val mime = app.contentResolver.getType(uri) ?: Kinds.mimeOf(name)
        val meta = withContext(Dispatchers.IO) { MediaMeta.read(app, uri, Kinds.kindOf(name)) }

        val body = JSONObject()
            .put("name", name)
            .put("parents", JSONArray().put(catId))
            .put("appProperties", JSONObject().put("pc", meta.pages.toString()).put("du", meta.durationMs.toString()))
        meta.thumb?.let {
            body.put(
                "contentHints",
                JSONObject().put(
                    "thumbnail",
                    JSONObject()
                        .put("image", Base64.encodeToString(it, Base64.URL_SAFE or Base64.NO_WRAP))
                        .put("mimeType", "image/jpeg")
                )
            )
        }

        var temp: File? = null
        try {
            val length: Long
            val open: () -> InputStream
            if (declared >= 0) {
                length = declared
                open = { app.contentResolver.openInputStream(uri) ?: throw IOException("Không đọc được tệp") }
            } else {
                // Nguồn không báo dung lượng: chép tạm để biết độ dài trước khi tải lên
                val t = File(cacheDir(), "upload_${System.nanoTime()}.tmp")
                temp = t
                withContext(Dispatchers.IO) {
                    val input = app.contentResolver.openInputStream(uri) ?: throw IOException("Không đọc được tệp")
                    input.use { i -> t.outputStream().use { o -> i.copyTo(o) } }
                }
                length = t.length()
                if (length > MAX_UPLOAD) throw IllegalStateException("\"$name\" lớn hơn giới hạn 1 GB")
                open = { t.inputStream() }
            }
            api.upload(body, mime, length, open, onProgress)
        } finally {
            temp?.delete()
        }
    }

    // ---------- Tải về / phát trực tiếp ----------

    suspend fun localFile(f: DocFile, onProgress: (Float) -> Unit = {}): File {
        val dir = cacheDir()
        val ext = Kinds.extOf(f.name).filter { it.isLetterOrDigit() }.take(10)
        val file = File(dir, if (ext.isEmpty()) f.id else "${f.id}.$ext")
        if (file.exists() && file.length() == f.size) return file

        val tmp = File(dir, "${f.id}.part")
        try {
            api.download(f.id, tmp, onProgress)
        } catch (e: Exception) {
            tmp.delete()
            throw e
        }
        withContext(Dispatchers.IO) {
            file.delete()
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
        }
        return file
    }

    /** URL + header xác thực để ExoPlayer phát trực tiếp từ Drive (có tua, không cần tải cả tệp). */
    suspend fun streamInfo(f: DocFile): Pair<String, Map<String, String>> = api.mediaUrl(f.id) to api.bearer()

    suspend fun saveCopy(f: DocFile, dest: Uri) {
        val src = localFile(f)
        withContext(Dispatchers.IO) {
            val out = app.contentResolver.openOutputStream(dest, "w")
                ?: throw IllegalStateException("Không ghi được vào vị trí đã chọn")
            out.use { o -> src.inputStream().use { it.copyTo(o) } }
        }
    }

    /** Bản sao mang đúng tên gốc, dùng cho "Mở bằng ứng dụng khác". */
    suspend fun exportCopy(f: DocFile): File {
        val src = localFile(f)
        return withContext(Dispatchers.IO) {
            val dir = File(cacheDir(), "out/${f.id}").apply { mkdirs() }
            val dst = File(dir, sanitizeName(f.name).ifEmpty { "tep" })
            if (!dst.exists() || dst.length() != src.length()) src.copyTo(dst, overwrite = true)
            dst
        }
    }

    private fun dropCache(f: DocFile) {
        val dir = cacheDir()
        dir.listFiles()?.filter { it.name.startsWith(f.id) }?.forEach { it.deleteRecursively() }
        File(dir, "out/${f.id}").deleteRecursively()
    }

    // ---------- Thao tác tài liệu ----------

    suspend fun setTrash(id: String, trash: Boolean) {
        api.patch(id, JSONObject().put("trashed", trash))
    }

    suspend fun rename(f: DocFile, name: String) {
        api.patch(f.id, JSONObject().put("name", name))
        // Bản sao "mở bằng ứng dụng khác" mang tên cũ nên bỏ đi, lần sau tạo lại với tên mới
        withContext(Dispatchers.IO) { File(cacheDir(), "out/${f.id}").deleteRecursively() }
    }

    suspend fun setNotes(id: String, notes: String) {
        api.patch(id, JSONObject().put("description", notes))
    }

    suspend fun setPageCount(id: String, pages: Int) {
        api.patch(id, JSONObject().put("appProperties", JSONObject().put("pc", pages.toString())))
    }

    suspend fun move(f: DocFile, category: String) {
        val from = catIds[f.category] ?: throw IllegalStateException("Không tìm thấy phân loại hiện tại")
        val to = catIds[category] ?: throw IllegalStateException("Không tìm thấy phân loại đích")
        if (from != to) api.patch(f.id, JSONObject(), addParents = to, removeParents = from)
    }

    suspend fun deleteForever(f: DocFile) {
        try {
            api.delete(f.id)
        } catch (e: DriveException) {
            if (e.code != 404) throw e
        }
        withContext(Dispatchers.IO) { dropCache(f) }
    }

    // ---------- Phân loại (mỗi phân loại là một thư mục con) ----------

    suspend fun addCategory(name: String) {
        api.createFolder(name, rootId ?: ensureRoot())
    }

    suspend fun renameCategory(old: String, new: String) {
        val id = catIds[old] ?: throw IllegalStateException("Không tìm thấy phân loại")
        api.patch(id, JSONObject().put("name", new))
    }

    suspend fun deleteCategory(name: String, fallback: String) {
        val id = catIds[name] ?: throw IllegalStateException("Không tìm thấy phân loại")
        val to = catIds[fallback] ?: throw IllegalStateException("Không tìm thấy phân loại thay thế")
        // Chuyển mọi tệp (kể cả trong thùng rác) sang phân loại khác trước khi bỏ thư mục
        api.list("'$id' in parents", "id").forEach {
            api.patch(it.getString("id"), JSONObject(), addParents = to, removeParents = id)
        }
        api.patch(id, JSONObject().put("trashed", true))
    }
}
