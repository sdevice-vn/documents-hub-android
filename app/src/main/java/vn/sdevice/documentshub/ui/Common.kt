package vn.sdevice.documentshub.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import vn.sdevice.documentshub.data.DocFile
import vn.sdevice.documentshub.data.Kind
import vn.sdevice.documentshub.data.Kinds
import vn.sdevice.documentshub.data.formatDuration
import vn.sdevice.documentshub.data.formatSize

val LocalDocImageLoader = staticCompositionLocalOf<ImageLoader?> { null }

fun kindIcon(kind: Kind): ImageVector = when (kind) {
    Kind.PDF -> Icons.Default.PictureAsPdf
    Kind.IMAGE -> Icons.Default.Image
    Kind.VIDEO -> Icons.Default.VideoFile
    Kind.AUDIO -> Icons.Default.AudioFile
    Kind.TEXT -> Icons.Default.Description
    Kind.OFFICE -> Icons.Default.TableChart
    Kind.ARCHIVE -> Icons.Default.FolderZip
    Kind.OTHER -> Icons.Default.InsertDriveFile
}

fun metaLine(f: DocFile): String = listOfNotNull(
    Kinds.extOf(f.name).uppercase().ifEmpty { f.kind.label },
    if (f.pageCount > 0) "${f.pageCount} trang" else null,
    if (f.duration > 0) formatDuration(f.duration) else null,
    formatSize(f.size)
).joinToString(" · ")

/** Ảnh xem trước từ Google Drive (nếu có), không thì biểu tượng theo loại tệp. */
@Composable
fun Thumb(f: DocFile, modifier: Modifier = Modifier, iconSize: Int = 40) {
    val loader = LocalDocImageLoader.current
    var failed by remember(f.id, f.thumbUrl) { mutableStateOf(false) }
    Box(modifier, contentAlignment = Alignment.Center) {
        if (f.thumbUrl != null && loader != null && !failed) {
            AsyncImage(
                model = f.thumbUrl,
                imageLoader = loader,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize(),
                onError = { failed = true }
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(kindIcon(f.kind), null, Modifier.size(iconSize.dp), tint = MaterialTheme.colorScheme.primary)
                val ext = Kinds.extOf(f.name).uppercase()
                if (ext.isNotEmpty() && iconSize >= 32) {
                    Text(ext, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Trả về hàm "Lưu bản sao": mở hộp chọn nơi lưu của hệ thống rồi chép tệp vào đó. */
@Composable
fun rememberSaveCopy(vm: MainViewModel): (DocFile) -> Unit {
    var target by remember { mutableStateOf<DocFile?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val f = target
        if (uri != null && f != null) vm.saveCopy(f, uri)
    }
    return { f ->
        target = f
        launcher.launch(f.name)
    }
}
