package app.treelune.core.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import app.treelune.R

/**
 * The app's mark as its launcher icon: the night and the drawing over it (ic_launcher_background,
 * ic_launcher_foreground), [size] square, cut to [shape]. A launcher shows the central 72 units
 * of a layer's 108, which is what [size] holds: the layers are drawn larger and cut.
 */
@Composable
fun LauncherMark(size: Dp, shape: Shape) {
    Box(modifier = Modifier.size(size).clip(shape), contentAlignment = Alignment.Center) {
        val layer = Modifier.requiredSize(size * LAYER / SHOWN)
        Image(painter = painterResource(R.drawable.ic_launcher_background), contentDescription = null, modifier = layer)
        Image(painter = painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = layer)
    }
}

/** A launcher layer's side, and the part of it a launcher shows, in its units. */
private const val LAYER = 108f
private const val SHOWN = 72f
