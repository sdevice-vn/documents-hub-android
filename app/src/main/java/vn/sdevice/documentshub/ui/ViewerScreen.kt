package vn.sdevice.documentshub.ui

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vn.sdevice.documentshub.data.DocFile
import vn.sdevice.documentshub.data.Kind
import vn.sdevice.documentshub.data.Kinds
import java.io.File

private sealed interface Load {
    data object Starting : Load
    data class Progress(val p: Float) : Load
    data class Ready(val file: File) : Load
    data class Failed(val message: String) : Load
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(vm: MainViewModel, file: DocFile, onClose: () -> Unit) {
    androidx.activity.compose.BackHandler(onBack = onClose)
    val saveCopy = rememberSaveCopy(vm)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, "Đóng") } },
                title = { Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 16.sp) },
                actions = {
                    IconButton(onClick = { saveCopy(file) }) { Icon(Icons.Default.Download, "Lưu bản sao") }
                    IconButton(onClick = { vm.openExternal(file) }) { Icon(Icons.Default.OpenInNew, "Mở bằng ứng dụng khác") }
                }
            )
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (file.kind) {
                Kind.VIDEO, Kind.AUDIO -> MediaViewer(vm, file)
                Kind.PDF, Kind.IMAGE, Kind.TEXT -> {
                    val state by rememberLocalFile(vm, file)
                    when (val s = state) {
                        Load.Starting -> LoadingBox(null)
                        is Load.Progress -> LoadingBox(s.p)
                        is Load.Failed -> MessageBox(s.message)
                        is Load.Ready -> when (file.kind) {
                            Kind.PDF -> PdfViewer(s.file) { vm.reportPageCount(file, it) }
                            Kind.IMAGE -> ImageViewer(s.file, file.name)
                            else -> TextViewer(s.file)
                        }
                    }
                }
                else -> FallbackViewer(vm, file, saveCopy)
            }
        }
    }
}

@Composable
private fun rememberLocalFile(vm: MainViewModel, file: DocFile) =
    produceState<Load>(Load.Starting, file.id) {
        value = try {
            Load.Ready(vm.localFile(file) { p -> value = Load.Progress(p) })
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            Load.Failed(e.message ?: "Không tải được tệp")
        }
    }

@Composable
private fun LoadingBox(progress: Float?) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        if (progress == null) {
            CircularProgressIndicator()
        } else {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.width(220.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text("Đang mở tệp", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MessageBox(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------------------------------------------------------------- PDF

@Composable
private fun PdfViewer(file: File, onPageCount: (Int) -> Unit) {
    val doc = remember(file) { runCatching { PdfDoc(file) } }
    DisposableEffect(doc) { onDispose { doc.getOrNull()?.close() } }
    val d = doc.getOrNull()
    if (d == null) {
        MessageBox("Không mở được PDF này (tệp có thể bị mã hóa hoặc hỏng).")
        return
    }
    LaunchedEffect(d) { onPageCount(d.pageCount) }

    var zoom by remember { mutableFloatStateOf(1f) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val pageBg = MaterialTheme.colorScheme.surfaceVariant

    BoxWithConstraints(Modifier.fillMaxSize().background(pageBg)) {
        val viewportWidth = maxWidth
        val viewportPx = with(LocalDensity.current) { viewportWidth.toPx() }
        val renderPx = (viewportPx * zoom).toInt().coerceAtMost(2200)

        Box(Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
            LazyColumn(
                state = listState,
                modifier = Modifier.width(viewportWidth * zoom).fillMaxHeight(),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(d.pageCount) { i -> PdfPage(d, i, renderPx) }
            }
        }

        val current = (listState.firstVisibleItemIndex + 1).coerceIn(1, d.pageCount)
        Surface(
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp,
            shadowElevation = 6.dp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp)
        ) {
            Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { scope.launch { listState.animateScrollToItem((current - 2).coerceAtLeast(0)) } }) {
                    Icon(Icons.Default.KeyboardArrowUp, "Trang trước")
                }
                Text("$current / ${d.pageCount}", fontSize = 13.sp)
                IconButton(onClick = { scope.launch { listState.animateScrollToItem(current.coerceAtMost(d.pageCount - 1)) } }) {
                    Icon(Icons.Default.KeyboardArrowDown, "Trang sau")
                }
                IconButton(onClick = { zoom = (zoom - 0.25f).coerceAtLeast(1f) }) { Icon(Icons.Default.ZoomOut, "Thu nhỏ") }
                Text("${(zoom * 100).toInt()}%", fontSize = 13.sp)
                IconButton(onClick = { zoom = (zoom + 0.25f).coerceAtMost(3f) }) { Icon(Icons.Default.ZoomIn, "Phóng to") }
                IconButton(onClick = { zoom = 1f }) { Icon(Icons.Default.FitScreen, "Vừa chiều rộng") }
            }
        }
    }
}

@Composable
private fun PdfPage(doc: PdfDoc, index: Int, widthPx: Int) {
    val bmp by produceState<ImageBitmap?>(null, doc, index, widthPx) {
        value = runCatching { doc.render(index, widthPx).asImageBitmap() }.getOrNull() ?: value
    }
    val b = bmp
    val ratio = if (b != null) b.width.toFloat() / b.height else 0.707f
    Box(
        Modifier.fillMaxWidth().aspectRatio(ratio).background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        if (b != null) Image(b, "Trang ${index + 1}", Modifier.fillMaxSize(), contentScale = ContentScale.FillWidth)
        else CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
    }
}

// ---------------------------------------------------------------- Ảnh

@Composable
private fun ImageViewer(file: File, name: String) {
    val ctx = LocalContext.current
    val loader = remember { ImageLoader.Builder(ctx).components { add(SvgDecoder.Factory()) }.build() }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 6f)
                    offset = if (scale <= 1f) Offset.Zero else offset + pan
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    scale = if (scale > 1f) 1f else 2.5f
                    offset = Offset.Zero
                })
            }
    ) {
        AsyncImage(
            model = ImageRequest.Builder(ctx).data(file).build(),
            imageLoader = loader,
            contentDescription = name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().graphicsLayer(
                scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y
            )
        )
    }
}

// ---------------------------------------------------------------- Văn bản

private data class TextResult(val lines: List<String>, val truncated: Boolean)

private fun readTextFile(file: File, maxBytes: Int = 1_048_576): TextResult {
    val size = file.length()
    val buf = ByteArray(minOf(size, maxBytes.toLong()).toInt())
    file.inputStream().use { input ->
        var off = 0
        while (off < buf.size) {
            val n = input.read(buf, off, buf.size - off)
            if (n <= 0) break
            off += n
        }
    }
    val text: String = if (buf.size >= 2 && buf[0] == 0xFF.toByte() && buf[1] == 0xFE.toByte()) {
        String(buf, 2, buf.size - 2, Charsets.UTF_16LE)
    } else {
        if (buf.take(4096).any { it == 0.toByte() }) throw IllegalStateException("Tệp này không phải văn bản thuần")
        String(buf, Charsets.UTF_8).removePrefix("\uFEFF")
    }
    return TextResult(text.replace("\r\n", "\n").split('\n'), size > maxBytes)
}

@Composable
private fun TextViewer(file: File) {
    val result by produceState<Result<TextResult>?>(null, file) {
        value = withContext(Dispatchers.IO) { runCatching { readTextFile(file) } }
    }
    val r = result
    when {
        r == null -> LoadingBox(null)
        r.isFailure -> MessageBox(r.exceptionOrNull()?.message ?: "Không đọc được tệp")
        else -> {
            val t = r.getOrThrow()
            SelectionContainer {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
                    itemsIndexed(t.lines) { _, line ->
                        Text(line.ifEmpty { " " }, fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 19.sp)
                    }
                    if (t.truncated) {
                        item {
                            Text(
                                "Chỉ hiển thị 1 MB đầu của tệp. Hãy lưu bản sao để xem đầy đủ.",
                                modifier = Modifier.padding(top = 16.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Video / âm thanh (phát trực tiếp, có tua)

@Composable
private fun MediaViewer(vm: MainViewModel, file: DocFile) {
    val ctx: Context = LocalContext.current
    val src by produceState<Result<Pair<String, Map<String, String>>>?>(null, file.id) {
        value = runCatching { vm.streamInfo(file) }
    }
    var playbackError by remember(file.id) { mutableStateOf<String?>(null) }

    val s = src
    if (s == null) { LoadingBox(null); return }
    val info = s.getOrNull()
    if (info == null) { MessageBox(s.exceptionOrNull()?.message ?: "Không phát được tệp"); return }

    val player = remember(file.id) {
        val http = DefaultHttpDataSource.Factory().setDefaultRequestProperties(info.second)
        ExoPlayer.Builder(ctx)
            .setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .build()
            .apply {
                setMediaItem(MediaItem.Builder().setUri(info.first).setMimeType(Kinds.mimeOf(file.name)).build())
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        playbackError = "Định dạng này không phát được trong app. Hãy thử \"Mở bằng ứng dụng khác\"."
                    }
                })
                prepare()
                playWhenReady = true
            }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(player, lifecycle) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_STOP) player.pause() }
        lifecycle.addObserver(obs)
        onDispose {
            lifecycle.removeObserver(obs)
            player.release()
        }
    }

    val audio = file.kind == Kind.AUDIO
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
                    this.player = player
                    useController = true
                    controllerShowTimeoutMs = if (audio) 0 else 3000
                    if (audio) showController()
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        playbackError?.let {
            Text(
                it, color = Color.White, textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(32.dp)
            )
        }
    }
}

// ---------------------------------------------------------------- Định dạng khác (Office, nén, ...)

@Composable
private fun FallbackViewer(vm: MainViewModel, file: DocFile, saveCopy: (DocFile) -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 4.dp) {
            Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                Icon(kindIcon(file.kind), null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(file.name, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "App chưa xem trực tiếp được định dạng này. Tệp vẫn được lưu an toàn; bạn có thể mở bằng ứng dụng khác trên máy hoặc lưu một bản sao.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = { vm.openExternal(file) }) { Text("Mở bằng ứng dụng khác") }
        Spacer(Modifier.height(8.dp))
        FilledTonalButton(onClick = { saveCopy(file) }) { Text("Lưu bản sao") }
    }
}
