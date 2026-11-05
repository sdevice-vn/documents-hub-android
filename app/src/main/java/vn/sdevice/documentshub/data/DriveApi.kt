package vn.sdevice.documentshub.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSink
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class DriveException(val code: Int, val reason: String?, message: String) : Exception(message)

private class StreamBody(
    private val type: MediaType?,
    private val length: Long,
    private val open: () -> InputStream,
    private val onProgress: (Float) -> Unit
) : RequestBody() {
    override fun contentType() = type
    override fun contentLength() = length
    override fun writeTo(sink: BufferedSink) {
        open().use { input ->
            val buf = ByteArray(64 * 1024)
            var sent = 0L
            var last = 0f
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                sink.write(buf, 0, n)
                sent += n
                val p = if (length > 0) sent.toFloat() / length else 1f
                if (p - last >= 0.01f || p >= 1f) {
                    last = p
                    onProgress(p)
                }
            }
        }
    }
}

/** Gọi Google Drive REST v3 bằng OkHttp (không cần thư viện Google API nặng). */
class DriveApi(private val auth: DriveAuth) {
    companion object {
        const val API = "https://www.googleapis.com/drive/v3"
        const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
        const val FOLDER = "application/vnd.google-apps.folder"
        const val FILE_FIELDS = "id,name,size,description,parents,createdTime,modifiedTime,trashed,appProperties,thumbnailLink"
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                cont.resume(response)
            }
        })
    }

    private fun url(base: String, params: Map<String, String> = emptyMap()): String {
        val b = base.toHttpUrl().newBuilder()
        params.forEach { (k, v) -> b.addQueryParameter(k, v) }
        return b.build().toString()
    }

    private fun parseError(code: Int, text: String): DriveException {
        var reason: String? = null
        var msg = "Google Drive trả về lỗi $code"
        runCatching {
            val e = JSONObject(text).getJSONObject("error")
            msg = e.optString("message", msg)
            reason = e.optJSONArray("errors")?.optJSONObject(0)?.optString("reason")
        }
        return DriveException(code, reason, msg)
    }

    private suspend fun call(
        url: String,
        method: String = "GET",
        body: RequestBody? = null,
        headers: Map<String, String> = emptyMap()
    ): Response {
        val token = auth.token()
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .method(method, body)
            .build()
        val resp = http.newCall(req).await()
        if (!resp.isSuccessful) {
            val text = resp.use { it.body?.string().orEmpty() }
            throw parseError(resp.code, text)
        }
        return resp
    }

    private suspend fun json(url: String, method: String = "GET", body: JSONObject? = null): JSONObject =
        call(url, method, body?.toString()?.toRequestBody(jsonType)).use { JSONObject(it.body?.string().orEmpty().ifEmpty { "{}" }) }

    // ---------- Thông tin ----------

    suspend fun aboutEmail(): String? =
        json(url("$API/about", mapOf("fields" to "user(emailAddress)"))).optJSONObject("user")?.optString("emailAddress")

    suspend fun get(id: String, fields: String): JSONObject =
        json(url("$API/files/$id", mapOf("fields" to fields)))

    suspend fun list(q: String, fields: String, orderBy: String? = null): List<JSONObject> {
        val out = mutableListOf<JSONObject>()
        var page: String? = null
        do {
            val params = mutableMapOf(
                "q" to q,
                "fields" to "nextPageToken,files($fields)",
                "pageSize" to "1000",
                "spaces" to "drive"
            )
            if (orderBy != null) params["orderBy"] = orderBy
            if (page != null) params["pageToken"] = page
            val r = json(url("$API/files", params))
            val arr = r.optJSONArray("files")
            if (arr != null) for (i in 0 until arr.length()) out += arr.getJSONObject(i)
            page = r.optString("nextPageToken").ifEmpty { null }
        } while (page != null)
        return out
    }

    // ---------- Thay đổi ----------

    suspend fun createFolder(name: String, parentId: String?): String {
        val body = JSONObject().put("name", name).put("mimeType", FOLDER)
        if (parentId != null) body.put("parents", JSONArray().put(parentId))
        return json(url("$API/files", mapOf("fields" to "id")), "POST", body).getString("id")
    }

    suspend fun patch(id: String, body: JSONObject, addParents: String? = null, removeParents: String? = null) {
        val params = mutableMapOf("fields" to "id")
        if (addParents != null) params["addParents"] = addParents
        if (removeParents != null) params["removeParents"] = removeParents
        json(url("$API/files/$id", params), "PATCH", body)
    }

    suspend fun delete(id: String) {
        call(url("$API/files/$id"), "DELETE").close()
    }

    // ---------- Nội dung ----------

    fun mediaUrl(id: String) = url("$API/files/$id", mapOf("alt" to "media"))

    suspend fun bearer(): Map<String, String> = mapOf("Authorization" to "Bearer ${auth.token()}")

    suspend fun download(id: String, dest: File, onProgress: (Float) -> Unit) {
        call(mediaUrl(id)).use { r ->
            val body = r.body ?: throw IOException("Phản hồi rỗng từ Google Drive")
            val total = body.contentLength()
            withContext(Dispatchers.IO) {
                body.byteStream().use { input ->
                    dest.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        var last = 0f
                        while (true) {
                            ensureActive()
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            if (total > 0) {
                                val p = done.toFloat() / total
                                if (p - last >= 0.01f) {
                                    last = p
                                    onProgress(p)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /** Tải lên kiểu resumable: tạo phiên rồi gửi nội dung (stream từ Uri, không nạp cả tệp vào RAM). */
    suspend fun upload(
        metadata: JSONObject,
        mime: String,
        length: Long,
        open: () -> InputStream,
        onProgress: (Float) -> Unit
    ): JSONObject {
        val init = call(
            url("$UPLOAD/files", mapOf("uploadType" to "resumable", "fields" to FILE_FIELDS)),
            "POST",
            metadata.toString().toRequestBody(jsonType),
            mapOf("X-Upload-Content-Type" to mime, "X-Upload-Content-Length" to length.toString())
        )
        val location = init.use { it.header("Location") }
            ?: throw IllegalStateException("Google Drive không trả về địa chỉ tải lên")
        val put = call(location, "PUT", StreamBody(mime.toMediaTypeOrNull(), length, open, onProgress))
        return put.use { JSONObject(it.body?.string().orEmpty()) }
    }
}
