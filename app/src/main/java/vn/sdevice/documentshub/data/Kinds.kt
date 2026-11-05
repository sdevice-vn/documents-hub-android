package vn.sdevice.documentshub.data

import android.webkit.MimeTypeMap

enum class Kind(val label: String) {
    PDF("PDF"), IMAGE("Hình ảnh"), VIDEO("Video"), AUDIO("Âm thanh"),
    TEXT("Văn bản"), OFFICE("Office"), ARCHIVE("Nén"), OTHER("Tệp")
}

object Kinds {
    private val groups: Map<Kind, List<String>> = mapOf(
        Kind.PDF to listOf("pdf"),
        Kind.IMAGE to listOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "svg", "ico", "avif", "heic"),
        Kind.VIDEO to listOf("mp4", "m4v", "webm", "mov", "mkv", "avi", "wmv", "flv", "mpg", "mpeg", "3gp", "ogv"),
        Kind.AUDIO to listOf("mp3", "wav", "ogg", "oga", "m4a", "flac", "aac", "opus", "wma"),
        Kind.TEXT to listOf(
            "txt", "md", "markdown", "log", "json", "csv", "tsv", "xml", "yml", "yaml", "ini", "cfg", "conf",
            "toml", "properties", "html", "htm", "css", "js", "mjs", "ts", "tsx", "jsx", "py", "c", "h", "cpp",
            "hpp", "cc", "ino", "java", "kt", "kts", "gradle", "go", "rs", "sh", "bat", "cmd", "ps1", "sql",
            "php", "rb", "lua", "tex", "srt", "vtt"
        ),
        Kind.OFFICE to listOf(
            "doc", "docx", "dot", "dotx", "odt", "rtf", "xls", "xlsx", "xlsm", "ods", "ppt", "pptx", "pps",
            "ppsx", "odp", "pages", "numbers", "key"
        ),
        Kind.ARCHIVE to listOf("zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz", "iso")
    )

    private val byExt: Map<String, Kind> = buildMap {
        groups.forEach { (kind, exts) -> exts.forEach { put(it, kind) } }
    }

    // Tệp thực thi / script: không bao giờ mở trực tiếp bằng ứng dụng khác
    private val risky = setOf(
        "exe", "com", "scr", "pif", "msi", "msp", "bat", "cmd", "ps1", "psm1", "vbs", "vbe", "js", "jse",
        "wsf", "wsh", "hta", "jar", "lnk", "reg", "cpl", "dll", "sys", "appx", "msix", "application",
        "gadget", "apk", "apks", "xapk", "aab"
    )

    fun extOf(name: String): String {
        val i = name.lastIndexOf('.')
        return if (i > 0) name.substring(i + 1).lowercase() else ""
    }

    fun kindOf(name: String): Kind = byExt[extOf(name)] ?: Kind.OTHER

    fun mimeOf(name: String): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(extOf(name)) ?: "application/octet-stream"

    fun isRisky(name: String): Boolean = extOf(name) in risky
}
