package vn.sdevice.documentshub.data

data class DocFile(
    val id: String,
    val name: String,
    val size: Long,
    val category: String,
    val notes: String,
    val uploadDate: Long,
    val isTrash: Boolean,
    val trashedAt: Long,
    val pageCount: Int,
    val duration: Long,
    val thumbUrl: String?
) {
    val kind: Kind get() = Kinds.kindOf(name)
}
