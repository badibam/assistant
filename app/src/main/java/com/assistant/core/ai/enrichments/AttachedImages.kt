package com.assistant.core.ai.enrichments

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.assistant.core.database.AppDatabase
import com.assistant.core.utils.LogManager
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The files of the images joined to messages: one reduced JPEG per image, flat in
 * `files/attachments/`, named by its id (AttachedImageEntity describes it). What is kept is what
 * the model sees: never the original.
 */
object AttachedImages {

    /** The longest side an image is reduced to: every model reads it without reducing it again. */
    const val LONG_SIDE = 1568

    /** The JPEG quality it is written at: 200 to 400 KB for a photo. */
    const val JPEG_QUALITY = 85

    private const val DIR = "attachments"
    private const val EXTENSION = ".jpg"
    private const val PART = ".part"

    fun dir(context: Context): File = File(context.filesDir, DIR)

    /** The file of image [id]. */
    fun file(context: Context, id: String): File = File(dir(context), "$id$EXTENSION")

    /**
     * [bytes] written as image [id]: first as `<id>.jpg.part`, then renamed, so a final file is
     * always complete.
     */
    fun write(context: Context, id: String, bytes: ByteArray) {
        val dir = dir(context).apply { mkdirs() }
        val part = File(dir, "$id$EXTENSION$PART")
        part.writeBytes(bytes)
        check(part.renameTo(file(context, id))) { "Image $id: ${part.name} not renamed" }
    }

    /** The file of image [id] deleted; one already gone is no error. */
    fun delete(context: Context, id: String) {
        val file = file(context, id)
        if (file.exists() && !file.delete()) LogManager.aiEnrichment("Image file ${file.name} not deleted", "ERROR")
    }

    /** An image ready to keep: its JPEG bytes and its size in pixels. */
    class Prepared(val bytes: ByteArray, val width: Int, val height: Int)

    /**
     * The image at [uri] prepared to be kept: turned upright as the camera noted it, reduced to
     * [LONG_SIDE] on its longest side (never enlarged), written as JPEG at [JPEG_QUALITY]. Writing
     * it anew leaves every EXIF tag behind, the GPS position among them.
     *
     * @throws IllegalArgumentException if [uri] is not an image Android can read
     */
    fun prepare(resolver: ContentResolver, uri: Uri): Prepared {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Not an image: $uri" }

        val orientation = resolver.openInputStream(uri).use { stream ->
            stream?.let { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        } ?: ExifInterface.ORIENTATION_NORMAL

        // Decoded at a power-of-two fraction that keeps it at least as large as needed, so a
        // 50 MP photo never sits whole in memory
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight) }
        val decoded = resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IllegalArgumentException("Image not decoded: $uri")

        val (width, height) = reducedSize(decoded.width, decoded.height)
        val matrix = orientationMatrix(orientation).apply {
            preScale(width.toFloat() / decoded.width, height.toFloat() / decoded.height)
        }
        val upright = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (upright !== decoded) decoded.recycle()

        val bytes = ByteArrayOutputStream().use { out ->
            upright.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            out.toByteArray()
        }
        val prepared = Prepared(bytes, upright.width, upright.height)
        upright.recycle()
        return prepared
    }

    /** The size [width] × [height] takes once its longest side is at most [LONG_SIDE]. */
    fun reducedSize(width: Int, height: Int): Pair<Int, Int> {
        val longest = max(width, height)
        if (longest <= LONG_SIDE) return width to height
        val ratio = LONG_SIDE.toDouble() / longest
        return max(1, (width * ratio).roundToInt()) to max(1, (height * ratio).roundToInt())
    }

    /** The largest power of two dividing [width] × [height] while its longest side stays at least [LONG_SIDE]. */
    fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (max(width, height) / (sample * 2) >= LONG_SIDE) sample *= 2
        return sample
    }

    /** The turn and the mirror an EXIF orientation asks for. */
    private fun orientationMatrix(orientation: Int): Matrix = Matrix().apply {
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { postRotate(90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { postRotate(270f); postScale(-1f, 1f) }
        }
    }

    /** What the startup sweep removes from the folder: the parts left, and the images no row names. */
    data class Sweep(val parts: List<String>, val orphans: List<String>)

    /**
     * The files of the folder, by [names], that no image of [ids] is: a `.part` is a write the app
     * was killed in, expected; a `.jpg` without a row comes from a hard stop or a deletion path
     * that forgot the file. Anything else in the folder is left alone.
     */
    fun sweep(names: List<String>, ids: Set<String>): Sweep = Sweep(
        parts = names.filter { it.endsWith(PART) },
        orphans = names.filter { it.endsWith(EXTENSION) && it.removeSuffix(EXTENSION) !in ids }
    )

    @Volatile private var swept = false

    /**
     * The folder confronted with the table once per process, before any screen: nothing can be
     * joining an image then. Each file removed is logged — INFO for a part, WARN by its name for
     * an image without a row.
     */
    suspend fun sweepOnce(context: Context) {
        if (swept) return
        swept = true
        val names = dir(context).list()?.toList() ?: return
        val ids = AppDatabase.getDatabase(context).attachedImageDao().allIds().toSet()
        val sweep = sweep(names, ids)
        sweep.parts.forEach { name ->
            File(dir(context), name).delete()
            LogManager.aiEnrichment("Startup sweep: interrupted image write $name removed", "INFO")
        }
        sweep.orphans.forEach { name ->
            File(dir(context), name).delete()
            LogManager.aiEnrichment("Startup sweep: image file $name named by no row, removed", "WARN")
        }
    }
}
