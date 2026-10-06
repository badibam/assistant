package com.assistant.themes.cosy

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/*
 * The pieces every cosy component is made of (docs/design/cosy-theme.md): a piece raised on its
 * full shadow, which sinks into it under the finger and springs back up past its place before it
 * settles; a window that swells in.
 */

/** How long a piece takes to sink into its shadow: quick, the finger is already down. */
private const val SINK_MS = 70

/** The spring a piece comes back on: loose enough to pass its place once, a quarter of a second or so. */
private val BOUNCE = spring<Float>(dampingRatio = 0.42f, stiffness = Spring.StiffnessMediumLow)

/** The spring a window swells in on: the same overshoot, a little firmer. */
private val SWELL = spring<Float>(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium)

/**
 * Something touched, told whether a finger is down on it. No ripple: the piece sinking shows it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun Pressable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    content: @Composable BoxScope.(pressed: Boolean) -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(modifier = modifier.combinedClickable(interactionSource = source, indication = null, enabled = enabled, onLongClick = onLongClick, onClick = onClick)) {
        content(pressed)
    }
}

/**
 * A piece standing on its full shadow, [depth] under it: the room for the shadow is taken under the
 * piece, inside [modifier]'s size. [pressed], it sinks into the shadow, which it then hides; let go,
 * it springs back. A [depth] of zero is a piece lying flat (a disabled button, a well).
 */
@Composable
internal fun Raised(
    modifier: Modifier = Modifier,
    shape: Shape,
    fill: Color,
    shadow: Color,
    depth: Dp,
    pressed: Boolean = false,
    border: Color? = null,
    borderWidth: Dp = 0.dp,
    padding: Dp = 0.dp,
    /** The content's own size within the piece: filling it, for a tile the grid sizes. */
    contentModifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    // 0 at rest, 1 sunk to the bottom of the shadow; under 0 while it springs past its place
    val sink = remember { Animatable(0f) }
    LaunchedEffect(pressed) {
        if (pressed) sink.animateTo(1f, tween(SINK_MS)) else sink.animateTo(0f, BOUNCE)
    }
    Box(modifier = modifier.padding(bottom = depth)) {
        Box(
            modifier = Modifier
                .matchParentSize()
                // The shadow, outside the layer that moves: it stays where it is as the piece sinks
                .drawBehind {
                    if (depth > 0.dp) {
                        val outline = shape.createOutline(size, layoutDirection, this)
                        translate(top = depth.toPx()) { drawOutline(outline, shadow) }
                    }
                }
                .graphicsLayer { translationY = sink.value * depth.toPx() }
                .clip(shape)
                .background(fill)
                .let { m -> if (border != null) m.border(borderWidth, border, shape) else m }
        )
        Box(
            modifier = contentModifier
                .graphicsLayer { translationY = sink.value * depth.toPx() }
                .padding(padding),
            content = content
        )
    }
}

/**
 * A window's content arriving: from a little smaller and faded, it swells to its size and passes it
 * once. It leaves at once, which is the window closing.
 */
@Composable
internal fun SwellIn(content: @Composable () -> Unit) {
    val scale = remember { Animatable(SWELL_FROM) }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { alpha.animateTo(1f, tween(FADE_MS)) }
        scale.animateTo(1f, SWELL)
    }
    Box(modifier = Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value; this.alpha = alpha.value }) { content() }
}

private const val SWELL_FROM = 0.86f
private const val FADE_MS = 120

/**
 * An item lifted from its list (a reorder in progress) or from the grid: a little larger, tilted a
 * few degrees; put down, it springs back to its place.
 */
@Composable
internal fun Lifted(lifted: Boolean, content: @Composable () -> Unit) {
    val amount by animateFloatAsState(if (lifted) 1f else 0f, BOUNCE, label = "lift")
    Box(
        modifier = Modifier.graphicsLayer {
            val scale = 1f + LIFT_SCALE * amount
            scaleX = scale
            scaleY = scale
            rotationZ = LIFT_TILT * amount
        }
    ) { content() }
}

private const val LIFT_SCALE = 0.04f
private const val LIFT_TILT = -2.5f
