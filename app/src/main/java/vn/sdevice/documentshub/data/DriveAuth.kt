package vn.sdevice.documentshub.data

import android.accounts.Account
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.AccountPicker
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.tasks.await

/** Cần người dùng đồng ý (chọn tài khoản / cấp quyền Drive). Giao diện sẽ mở hộp thoại của Google. */
class NeedsConsent(val pendingIntent: PendingIntent) : Exception("Cần cấp quyền truy cập Google Drive")

/**
 * Lấy access token cho quyền drive.file: app chỉ thấy các tệp/thư mục do chính nó tạo.
 * Sau lần đồng ý đầu tiên, các lần sau lấy token âm thầm, không hiện hộp thoại.
 */
class DriveAuth(context: Context) {
    private val ctx = context.applicationContext
    /** Tài khoản Google người dùng đã chọn; luôn lấy token cho đúng tài khoản này. */
    @Volatile
    var account: Account? = null

    private fun request(): AuthorizationRequest {
        val b = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope("https://www.googleapis.com/auth/drive.file")))
        account?.let { b.setAccount(it) }
        return b.build()
    }

    /** Hộp chọn tài khoản của Google, luôn hiện danh sách (không tự dùng lại tài khoản cũ). */
    @Suppress("DEPRECATION")
    fun chooseAccountIntent(): Intent =
        AccountPicker.newChooseAccountIntent(null, null, arrayOf("com.google"), true, null, null, null, null)

    @Volatile
    var lastToken: String? = null
        private set

    suspend fun token(): String {
        val result = Identity.getAuthorizationClient(ctx).authorize(request()).await()
        if (result.hasResolution()) {
            throw NeedsConsent(result.pendingIntent ?: throw IllegalStateException("Thiếu hộp thoại cấp quyền"))
        }
        val t = result.accessToken ?: throw IllegalStateException("Không lấy được mã truy cập Google")
        lastToken = t
        return t
    }

    /** Kết quả trả về từ hộp thoại đồng ý của Google. */
    fun fromIntent(data: Intent?): String {
        val result = Identity.getAuthorizationClient(ctx).getAuthorizationResultFromIntent(data)
        val t = result.accessToken ?: throw IllegalStateException("Không lấy được mã truy cập Google")
        lastToken = t
        return t
    }

    fun forget() {
        lastToken = null
    }
}
