package vn.sdevice.documentshub.data

import java.text.DateFormat
import java.util.Date
import java.util.Locale

private val VI: Locale = Locale.forLanguageTag("vi")

val DEFAULT_CATEGORIES = listOf("Công việc", "Học tập", "Hóa đơn", "Cá nhân", "Khác")

fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB")
    var v = bytes.toDouble()
    var i = -1
    do {
        v /= 1024
        i++
    } while (v >= 1024 && i < units.lastIndex)
    return String.format(VI, "%.1f %s", v, units[i])
}

fun formatDate(ms: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM, VI).format(Date(ms))

fun formatDuration(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(VI, "%d:%02d:%02d", h, m, s) else String.format(VI, "%d:%02d", m, s)
}

/** Làm sạch tên tệp / tên phân loại: bỏ ký tự cấm, giới hạn độ dài, giữ lại đuôi tệp. */
fun sanitizeName(raw: String): String {
    var n = raw.replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001f]"), "")
        .trimStart('.')
        .trimEnd('.', ' ')
        .trim()
    if (n.length > 150) {
        val dot = n.lastIndexOf('.')
        val ext = if (dot > 0) n.substring(dot).take(16) else ""
        n = n.take(150 - ext.length).trimEnd() + ext
    }
    return n
}
