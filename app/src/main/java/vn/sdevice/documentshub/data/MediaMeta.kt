package vn.sdevice.documentshub.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.ByteArrayOutputStream

/** Tạo ảnh xem trước nhỏ (JPEG ~320px), số trang PDF, thời lượng video/âm thanh trước khi tải lên. */
object MediaMeta {
    private const val THUMB_W = 320

    data class Meta(val pages: Int = 0, val durationMs: Long = 0, val thumb: ByteArray? = null)

    fun read(ctx: Context, uri: Uri, kind: Kind): Meta = runCatching {
        when (kind) {
            Kind.PDF -> pdf(ctx, uri)
            Kind.IMAGE -> Meta(thumb = image(ctx, uri))
            Kind.VIDEO -> video(ctx, uri)
            Kind.AUDIO -> audio(ctx, uri)
            else -> Meta()
        }
    }.getOrDefault(Meta())

    private fun pdf(ctx: Context, uri: Uri): Meta {
        val pfd = ctx.contentResolver.openFileDescriptor(uri, "r") ?: return Meta()
        pfd.use {
            val renderer = PdfRenderer(it)
            try {
                val pages = renderer.pageCount
                val page = renderer.openPage(0)
                val thumb = try {
                    val w = THUMB_W
                    val h = (w * page.height.toFloat() / page.width).toInt().coerceAtLeast(1)
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    jpeg(bmp)
                } finally {
                    page.close()
                }
                return Meta(pages = pages, thumb = thumb)
            } finally {
                renderer.close()
            }
        }
    }

    private fun image(ctx: Context, uri: Uri): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= THUMB_W) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: return null
        return jpeg(bmp)
    }

    private fun video(ctx: Context, uri: Uri): Meta {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(ctx, uri)
            val dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val frame = r.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: r.getFrameAtTime(0)
            return Meta(durationMs = dur, thumb = frame?.let { jpeg(it) })
        } finally {
            r.release()
        }
    }

    private fun audio(ctx: Context, uri: Uri): Meta {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(ctx, uri)
            val dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            return Meta(durationMs = dur)
        } finally {
            r.release()
        }
    }

    // Thu nhỏ, đặt lên nền trắng (ảnh PNG trong suốt), nén JPEG
    private fun jpeg(src: Bitmap): ByteArray {
        val scale = if (src.width > THUMB_W) THUMB_W.toFloat() / src.width else 1f
        val w = (src.width * scale).toInt().coerceAtLeast(1)
        val h = (src.height * scale).toInt().coerceAtLeast(1)
        val flat = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(flat)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(Bitmap.createScaledBitmap(src, w, h, true), 0f, 0f, null)
        val out = ByteArrayOutputStream()
        flat.compress(Bitmap.CompressFormat.JPEG, 70, out)
        return out.toByteArray()
    }
}
