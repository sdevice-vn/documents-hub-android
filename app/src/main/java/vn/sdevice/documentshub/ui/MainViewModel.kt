package vn.sdevice.documentshub.ui

import android.accounts.Account
import android.app.Application
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil.ImageLoader
import coil.decode.SvgDecoder
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import vn.sdevice.documentshub.data.DEFAULT_CATEGORIES
import vn.sdevice.documentshub.data.DocFile
import vn.sdevice.documentshub.data.DriveAuth
import vn.sdevice.documentshub.data.DriveException
import vn.sdevice.documentshub.data.Kind
import vn.sdevice.documentshub.data.Kinds
import vn.sdevice.documentshub.data.NeedsConsent
import vn.sdevice.documentshub.data.Repository
import vn.sdevice.documentshub.data.sanitizeName
import java.io.File
import java.io.IOException

enum class Sort(val label: String) {
    NEWEST("Mới nhất"), OLDEST("Cũ nhất"), NAME("Tên A đến Z"), SIZE("Dung lượng lớn nhất")
}

enum class TypeFilter(val label: String, val kinds: Set<Kind>?) {
    ALL("Tất cả loại", null),
    PDF("PDF", setOf(Kind.PDF)),
    TEXT("Văn bản và Office", setOf(Kind.TEXT, Kind.OFFICE)),
    IMAGE("Hình ảnh", setOf(Kind.IMAGE)),
    VIDEO("Video", setOf(Kind.VIDEO)),
    AUDIO("Âm thanh", setOf(Kind.AUDIO)),
    OTHER("Khác", setOf(Kind.ARCHIVE, Kind.OTHER))
}

sealed interface Nav {
    data object All : Nav
    data object Trash : Nav
    data class Category(val name: String) : Nav
}

data class UploadState(val name: String, val index: Int, val total: Int, val progress: Float)

fun filterSort(files: List<DocFile>, nav: Nav, q: String, type: TypeFilter, sort: Sort): List<DocFile> {
    val query = q.trim().lowercase()
    val base = files
        .filter { f ->
            when (nav) {
                Nav.Trash -> f.isTrash
                Nav.All -> !f.isTrash
                is Nav.Category -> !f.isTrash && f.category == nav.name
            }
        }
        .filter { type.kinds == null || it.kind in type.kinds }
        .filter { query.isEmpty() || it.name.lowercase().contains(query) || it.notes.lowercase().contains(query) }
    return when (sort) {
        Sort.NEWEST -> base.sortedByDescending { it.uploadDate }
        Sort.OLDEST -> base.sortedBy { it.uploadDate }
        Sort.NAME -> base.sortedBy { it.name.lowercase() }
        Sort.SIZE -> base.sortedByDescending { it.size }
    }
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val auth = DriveAuth(app)
    private val repo = Repository(app, auth)
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    val messages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    private fun say(text: String) { messages.tryEmit(text) }

    /** Trình tải ảnh dùng chung: gắn token Drive khi tải ảnh xem trước, hỗ trợ SVG. */
    val imageLoader: ImageLoader = ImageLoader.Builder(app)
        .okHttpClient {
            OkHttpClient.Builder().addInterceptor { chain ->
                val t = auth.lastToken
                val req = chain.request()
                chain.proceed(
                    if (t != null && req.url.host.endsWith("googleusercontent.com")) {
                        req.newBuilder().header("Authorization", "Bearer $t").build()
                    } else req
                )
            }.build()
        }
        .components { add(SvgDecoder.Factory()) }
        .crossfade(true)
        .build()

    // ---------- Dữ liệu ----------
    private val _files = MutableStateFlow<List<DocFile>>(emptyList())
    val files: StateFlow<List<DocFile>> = _files
    private val _categories = MutableStateFlow(DEFAULT_CATEGORIES)
    val categories: StateFlow<List<String>> = _categories

    val signedIn = MutableStateFlow(prefs.getBoolean("signedIn", false))
    var email by mutableStateOf<String?>(prefs.getString("email", null))
        private set
    var consent by mutableStateOf<PendingIntent?>(null)
        private set
    var accountPicker by mutableStateOf<Intent?>(null)
        private set
    var loading by mutableStateOf(false)
        private set

    // ---------- Trạng thái giao diện ----------
    var nav by mutableStateOf<Nav>(Nav.All)
    var query by mutableStateOf("")
    var type by mutableStateOf(TypeFilter.ALL)
    var sort by mutableStateOf(Sort.NEWEST)
    var grid by mutableStateOf(prefs.getBoolean("grid", true))
        private set
    var openId by mutableStateOf<String?>(null)
    var upload by mutableStateOf<UploadState?>(null)
        private set
    var busy by mutableStateOf<String?>(null)
        private set
    var signingIn by mutableStateOf(false)
        private set

    val dark = MutableStateFlow<Boolean?>(if (prefs.contains("dark")) prefs.getBoolean("dark", false) else null)

    init {
        email?.let { auth.account = Account(it, "com.google") }
        if (signedIn.value) refresh()
    }

    fun setDark(value: Boolean) {
        prefs.edit().putBoolean("dark", value).apply()
        dark.value = value
    }

    fun changeGrid(value: Boolean) {
        prefs.edit().putBoolean("grid", value).apply()
        grid = value
    }

    private fun report(e: Throwable) {
        if (e is NeedsConsent) consent = e.pendingIntent else say(friendly(e))
    }

    private fun launchSafe(block: suspend () -> Unit) = viewModelScope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            report(e)
        }
    }

    // ---------- Đăng nhập ----------
    /** Bước 1: mở hộp chọn tài khoản của Google. */
    fun signIn() { accountPicker = auth.chooseAccountIntent() }

    fun accountPickerLaunched() { accountPicker = null }

    /** Bước 2: đã chọn tài khoản, xin quyền Drive cho đúng tài khoản đó. */
    fun onAccountPicked(name: String?) {
        if (name.isNullOrBlank()) {
            say("Đã hủy đăng nhập")
            return
        }
        auth.account = Account(name, "com.google")
        email = name
        prefs.edit().putString("email", name).apply()
        launchSafe {
            signingIn = true
            try {
                repo.signIn()
                afterSignIn()
            } finally {
                signingIn = false
            }
        }
    }

    fun consentLaunched() { consent = null }

    fun consentCancelled() { say("Đã hủy đăng nhập") }

    fun onConsentResult(data: Intent?) = launchSafe {
        auth.fromIntent(data)
        afterSignIn()
    }

    private suspend fun afterSignIn() {
        prefs.edit().putBoolean("signedIn", true).apply()
        signedIn.value = true
        runCatching { repo.accountEmail() }.getOrNull()?.let {
            email = it
            prefs.edit().putString("email", it).apply()
        }
        reload()
    }

    fun signOut() = launchSafe {
        auth.account = null
        repo.clearLocal()
        imageLoader.diskCache?.clear()
        imageLoader.memoryCache?.clear()
        prefs.edit().remove("signedIn").remove("email").apply()
        signedIn.value = false
        email = null
        _files.value = emptyList()
        openId = null
        nav = Nav.All
        query = ""
    }

    // ---------- Tải danh sách ----------
    fun refresh() = launchSafe { reload() }

    private suspend fun reload() {
        loading = true
        try {
            val lib = repo.loadLibrary()
            _categories.value = lib.categories.ifEmpty { DEFAULT_CATEGORIES }
            _files.value = lib.files
        } finally {
            loading = false
        }
    }

    /** Cập nhật giao diện ngay, gửi lên Drive sau; nếu lỗi thì trả về trạng thái cũ. */
    private fun mutate(optimistic: (List<DocFile>) -> List<DocFile>, done: String? = null, op: suspend () -> Unit) =
        launchSafe {
            val before = _files.value
            _files.value = optimistic(before)
            try {
                op()
                done?.let { say(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _files.value = before
                throw e
            }
        }

    // ---------- Tệp ----------
    fun addFiles(uris: List<Uri>, category: String) = launchSafe {
        var ok = 0
        var failed = 0
        var lastError = ""
        uris.forEachIndexed { i, uri ->
            upload = UploadState(repo.displayName(uri), i + 1, uris.size, 0f)
            try {
                repo.upload(uri, category) { p -> upload = upload?.copy(progress = p) }
                ok++
            } catch (e: CancellationException) {
                throw e
            } catch (e: NeedsConsent) {
                upload = null
                throw e
            } catch (e: Throwable) {
                failed++
                lastError = friendly(e)
            }
        }
        upload = null
        if (ok > 0) runCatching { reload() }
        if (ok > 0 && failed == 0) say(if (ok == 1) "Đã thêm 1 tệp" else "Đã thêm $ok tệp")
        else if (failed > 0) say("Không thêm được $failed tệp: $lastError")
    }

    fun trash(f: DocFile) = mutate(
        { l -> l.map { if (it.id == f.id) it.copy(isTrash = true, trashedAt = System.currentTimeMillis()) else it } },
        "Đã chuyển \"${f.name}\" vào thùng rác"
    ) { repo.setTrash(f.id, true) }

    fun restore(f: DocFile) = mutate(
        { l -> l.map { if (it.id == f.id) it.copy(isTrash = false, trashedAt = 0L) else it } },
        "Đã khôi phục \"${f.name}\""
    ) { repo.setTrash(f.id, false) }

    fun deleteForever(f: DocFile) = launchSafe {
        busy = "Đang xóa…"
        try {
            repo.deleteForever(f)
            _files.value = _files.value.filter { it.id != f.id }
            say("Đã xóa vĩnh viễn \"${f.name}\"")
        } finally {
            busy = null
        }
    }

    fun emptyTrash() = launchSafe {
        busy = "Đang dọn thùng rác…"
        try {
            for (f in _files.value.filter { it.isTrash }) {
                repo.deleteForever(f)
                _files.value = _files.value.filter { it.id != f.id }
            }
            say("Đã dọn sạch thùng rác")
        } finally {
            busy = null
        }
    }

    fun rename(f: DocFile, raw: String) = launchSafe {
        val name = sanitizeName(raw)
        if (name.isEmpty()) return@launchSafe say("Tên tệp không hợp lệ")
        if (name == f.name) return@launchSafe
        val before = _files.value
        _files.value = before.map { if (it.id == f.id) it.copy(name = name) else it }
        try {
            repo.rename(f, name)
            say("Đã đổi tên thành \"$name\"")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            _files.value = before
            throw e
        }
    }

    fun saveNotes(f: DocFile, notes: String) = mutate(
        { l -> l.map { if (it.id == f.id) it.copy(notes = notes.take(2000)) else it } }
    ) { repo.setNotes(f.id, notes.take(2000)) }

    fun move(f: DocFile, category: String) = mutate(
        { l -> l.map { if (it.id == f.id) it.copy(category = category) else it } }
    ) { repo.move(f, category) }

    fun reportPageCount(f: DocFile, pages: Int) {
        if (pages > 0 && f.pageCount != pages) {
            viewModelScope.launch { runCatching { repo.setPageCount(f.id, pages) } }
        }
    }

    // ---------- Xem / xuất tệp ----------
    suspend fun localFile(f: DocFile, onProgress: (Float) -> Unit): File = repo.localFile(f, onProgress)
    suspend fun streamInfo(f: DocFile) = repo.streamInfo(f)

    fun saveCopy(f: DocFile, dest: Uri) = launchSafe {
        busy = "Đang lưu bản sao…"
        try {
            repo.saveCopy(f, dest)
            say("Đã lưu bản sao")
        } finally {
            busy = null
        }
    }

    fun openExternal(f: DocFile) = launchSafe {
        if (Kinds.isRisky(f.name)) {
            say("Vì an toàn, app không mở trực tiếp tệp thực thi hoặc script")
            return@launchSafe
        }
        busy = "Đang chuẩn bị tệp…"
        try {
            val app = getApplication<Application>()
            val copy = repo.exportCopy(f)
            val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", copy)
            val view = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, Kinds.mimeOf(f.name))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(view, f.name).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try {
                app.startActivity(chooser)
            } catch (e: ActivityNotFoundException) {
                say("Không có ứng dụng nào mở được loại tệp này")
            }
        } finally {
            busy = null
        }
    }

    // ---------- Phân loại ----------
    fun addCategory(raw: String) = launchSafe {
        val name = sanitizeName(raw)
        if (name.isEmpty()) return@launchSafe say("Tên phân loại không hợp lệ")
        if (categories.value.any { it.equals(name, ignoreCase = true) }) return@launchSafe say("Tên phân loại này đã tồn tại!")
        repo.addCategory(name)
        reload()
        say("Đã thêm phân loại \"$name\"")
    }

    fun renameCategory(old: String, raw: String) = launchSafe {
        val name = sanitizeName(raw)
        if (name.isEmpty()) return@launchSafe say("Tên phân loại không hợp lệ")
        if (name == old) return@launchSafe
        if (categories.value.any { it != old && it.equals(name, ignoreCase = true) }) return@launchSafe say("Tên phân loại này đã tồn tại!")
        repo.renameCategory(old, name)
        if ((nav as? Nav.Category)?.name == old) nav = Nav.Category(name)
        reload()
        say("Đã đổi tên phân loại")
    }

    fun deleteCategory(name: String) = launchSafe {
        val list = categories.value
        if (list.size <= 1) return@launchSafe say("Cần giữ lại ít nhất một phân loại")
        val fallback = list.first { it != name }
        busy = "Đang chuyển tệp sang \"$fallback\"…"
        try {
            repo.deleteCategory(name, fallback)
            if ((nav as? Nav.Category)?.name == name) nav = Nav.All
            reload()
            say("Đã xóa phân loại \"$name\", tệp được chuyển sang \"$fallback\"")
        } finally {
            busy = null
        }
    }

    private fun friendly(e: Throwable): String = when {
        e is ApiException && (e.statusCode == CommonStatusCodes.CANCELED || e.statusCode == 12501) -> "Đã hủy đăng nhập"
        e is ApiException && e.statusCode == CommonStatusCodes.NETWORK_ERROR -> "Không có kết nối mạng"
        e is DriveException && e.reason in setOf("storageQuotaExceeded", "quotaExceeded") -> "Google Drive đã hết dung lượng"
        e is DriveException && e.code == 401 -> "Phiên đăng nhập đã hết hạn, hãy thử lại"
        e is DriveException && e.code == 403 -> "Google Drive từ chối: ${e.message}"
        e is DriveException && e.code == 404 -> "Không tìm thấy tệp trên Google Drive (có thể đã bị xóa), hãy kéo xuống để làm mới"
        e is java.net.UnknownHostException || e is java.net.SocketTimeoutException || e is java.net.ConnectException ->
            "Không có kết nối mạng"
        e is IOException && e.message.isNullOrBlank() -> "Lỗi mạng hoặc đọc tệp"
        else -> e.message ?: e::class.java.simpleName
    }
}
