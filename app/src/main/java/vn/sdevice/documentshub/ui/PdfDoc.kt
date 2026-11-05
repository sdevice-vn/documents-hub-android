package vn.sdevice.documentshub.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Bọc PdfRenderer của Android: chỉ một trang được mở tại một thời điểm nên dùng Mutex. */
class PdfDoc(file: File) {
    private val pfd: ParcelFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer: PdfRenderer = try {
        PdfRenderer(pfd)
    } catch (e: Exception) {
        pfd.close()
        throw e
    }
    private val mutex = Mutex()

    val pageCount: Int = renderer.pageCount

    suspend fun render(index: Int, widthPx: Int): Bitmap = mutex.withLock {
        withContext(Dispatchers.IO) {
            val page = renderer.openPage(index)
            try {
                val w = widthPx.coerceAtLeast(1)
                val h = (w * page.height.toFloat() / page.width).toInt().coerceAtLeast(1)
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bmp
            } finally {
                page.close()
            }
        }
    }

    fun close() {
        runCatching { renderer.close() }
        runCatching { pfd.close() }
    }
}
