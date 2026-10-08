package app.treelune.core.ai.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.treelune.core.ai.data.MessageSegment
import app.treelune.core.ai.data.RichMessage
import app.treelune.core.ai.enrichments.AttachedImages
import app.treelune.core.strings.Strings
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI
import app.treelune.core.ui.components.FullScreenDialog
import app.treelune.core.utils.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

/** The height an image takes in the composer and in a message: a thumbnail, the whole image a touch away. */
val IMAGE_THUMBNAIL_HEIGHT: Dp = 96.dp

/** What reading an image's file gave: its picture, or that its file is missing. */
private sealed class ImageLoad {
    object Loading : ImageLoad()
    object Missing : ImageLoad()
    class Loaded(val bitmap: Bitmap) : ImageLoad()
}

/**
 * The image [imageId] read from its file, decoded no larger than [heightPx] needs (null: whole).
 * A missing file is said as such: a row without its file is an error, never hidden.
 */
@Composable
private fun rememberImage(imageId: String, heightPx: Int?): ImageLoad {
    val context = LocalContext.current
    val load by produceState<ImageLoad>(initialValue = ImageLoad.Loading, imageId, heightPx) {
        value = withContext(Dispatchers.IO) {
            val file = AttachedImages.file(context, imageId)
            if (!file.exists()) {
                LogManager.aiUI("Image $imageId: its file is missing", "ERROR")
                return@withContext ImageLoad.Missing
            }
            val options = BitmapFactory.Options()
            if (heightPx != null) {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, bounds)
                var sample = 1
                while (bounds.outHeight / (sample * 2) >= heightPx) sample *= 2
                options.inSampleSize = max(1, sample)
            }
            BitmapFactory.decodeFile(file.path, options)?.let { ImageLoad.Loaded(it) } ?: ImageLoad.Missing
        }
    }
    return load
}

/**
 * An image joined to a message, as a thumbnail; a touch shows it whole, over the screen, and a
 * touch or back closes it. A missing file reads as such in its place.
 */
@Composable
fun AttachedImageThumbnail(imageId: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val heightPx = with(LocalDensity.current) { IMAGE_THUMBNAIL_HEIGHT.roundToPx() }
    var open by rememberSaveable(imageId) { mutableStateOf(false) }

    when (val load = rememberImage(imageId, heightPx)) {
        ImageLoad.Loading -> Box(modifier.height(IMAGE_THUMBNAIL_HEIGHT))
        ImageLoad.Missing -> UI.Text(s.shared("ai_image_missing"), TextType.ERROR)
        is ImageLoad.Loaded -> Image(
            bitmap = load.bitmap.asImageBitmap(),
            contentDescription = s.shared("ai_image_block"),
            contentScale = ContentScale.Fit,
            alignment = Alignment.CenterStart,
            modifier = modifier.height(IMAGE_THUMBNAIL_HEIGHT).clickable { open = true }
        )
    }

    if (open) {
        FullScreenDialog(onDismiss = { open = false }) {
            Box(modifier = Modifier.fillMaxSize().clickable { open = false }.padding(UI.Space.M), contentAlignment = Alignment.Center) {
                when (val whole = rememberImage(imageId, null)) {
                    ImageLoad.Loading -> {}
                    ImageLoad.Missing -> UI.Text(s.shared("ai_image_missing"), TextType.ERROR)
                    is ImageLoad.Loaded -> Image(
                        bitmap = whole.bitmap.asImageBitmap(),
                        contentDescription = s.shared("ai_image_block"),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/**
 * A sent message that holds images, in its order: each run of text and blocks as the screen reads
 * it (rememberDisplayText), each image as a thumbnail between them.
 */
@Composable
fun RichMessageWithImages(message: RichMessage) {
    // The message cut where its images are: runs of other segments, and the images themselves
    val pieces = remember(message) {
        buildList<Any> {
            val run = mutableListOf<MessageSegment>()
            for (segment in message.segments) {
                if (segment is MessageSegment.Image) {
                    if (run.isNotEmpty()) { add(RichMessage(run.toList())); run.clear() }
                    add(segment)
                } else run.add(segment)
            }
            if (run.isNotEmpty()) add(RichMessage(run.toList()))
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
        pieces.forEach { piece ->
            when (piece) {
                is MessageSegment.Image -> AttachedImageThumbnail(piece.imageId)
                is RichMessage -> rememberDisplayText(piece).takeIf { it.isNotEmpty() }?.let { UI.Text(it, TextType.BODY) }
            }
        }
    }
}
