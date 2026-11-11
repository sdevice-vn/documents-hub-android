package vn.sdevice.documentshub

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat

/**
 * Dịch vụ chạy nền (foreground) chỉ để giữ app "đang hoạt động" trong lúc tải tệp lên.
 * Nhờ đó khi bạn chuyển sang app khác, Android không cắt mạng hoặc dừng app giữa chừng.
 * Việc tải lên vẫn do MainViewModel thực hiện; dịch vụ này chỉ hiện thông báo và giữ tiến trình sống.
 */
class UploadService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val text = intent?.getStringExtra(EXTRA_TEXT) ?: "Đang tải tệp lên…"
        createChannel()
        val notification = buildNotification(text)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            // Giữ CPU thức khi màn hình tắt, tối đa 3 giờ để không hao pin nếu có lỗi
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "documentshub:upload").apply {
                setReferenceCounted(false)
                acquire(3 * 60 * 60 * 1000L)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Tải tệp lên", NotificationManager.IMPORTANCE_LOW)
                )
            }
        }
    }

    private fun buildNotification(text: String): Notification {
        val open = android.app.PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_logo_layers)
            .setContentTitle("Documents Hub")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "upload"
        private const val NOTIFICATION_ID = 1001
        private const val EXTRA_TEXT = "text"

        @Volatile
        private var wanted = false

        /** Bắt đầu (hoặc cập nhật nội dung thông báo của) dịch vụ giữ app sống khi đang tải lên. */
        fun start(ctx: Context, text: String) {
            wanted = true
            val intent = Intent(ctx, UploadService::class.java).putExtra(EXTRA_TEXT, text)
            runCatching { ctx.startForegroundService(intent) }
        }

        /** Dừng dịch vụ. Chờ một chút phòng trường hợp vừa bắt đầu xong đã dừng (Android sẽ báo lỗi). */
        fun stop(ctx: Context) {
            wanted = false
            Handler(Looper.getMainLooper()).postDelayed({
                if (!wanted) ctx.stopService(Intent(ctx, UploadService::class.java))
            }, 1000)
        }
    }
}
