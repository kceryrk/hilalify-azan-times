package com.kceryrk.prayertimes

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/** Star position as fractions of the area, so the same sky draws at any size. [phase] offsets its twinkle. */
data class Star(val x: Float, val y: Float, val radiusDp: Float, val alpha: Float, val phase: Float)

object Stars {
    /** Fixed seed keeps the layout stable. y is squared so stars cluster toward the top. */
    fun field(count: Int): List<Star> {
        val rnd = Random(7L)
        return List(count) {
            val y = rnd.nextFloat().let { it * it }
            Star(rnd.nextFloat(), y, 0.4f + rnd.nextFloat() * 1.2f, 0.3f + rnd.nextFloat() * 0.7f, rnd.nextFloat())
        }
    }

    val home: List<Star> by lazy { field(90) }
}

/**
 * Draws stars in pure white. [twinkle] runs 0..1 over one cycle, and each star pulses at its own phase.
 * [spin] rotates the field in radians around a pivot far below the area, so it drifts like a slow sky rotation.
 */
fun DrawScope.drawStars(stars: List<Star>, twinkle: Float = 0f, spin: Float = 0f) {
    val pivot = Offset(size.width * 0.5f, size.height * 1.6f)
    val cosA = kotlin.math.cos(spin)
    val sinA = sin(spin)
    stars.forEach { s ->
        val wave = 0.5f + 0.5f * sin(2 * PI * (twinkle + s.phase)).toFloat()
        val px = s.x * size.width - pivot.x
        val py = s.y * size.height - pivot.y
        val center = Offset(pivot.x + px * cosA - py * sinA, pivot.y + px * sinA + py * cosA)
        drawCircle(
            color = Color.White.copy(alpha = s.alpha * (0.3f + 0.7f * wave)),
            radius = s.radiusDp.dp.toPx(),
            center = center
        )
    }
}

/** Draws a shooting star at random intervals. Call from drawBehind so the animation only redraws, not recomposes. */
@Composable
fun rememberSkyState(): SkyState {
    val transition = rememberInfiniteTransition(label = "sky")
    val twinkle = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart),
        label = "twinkle"
    )
    val spinState = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(120_000, easing = LinearEasing), RepeatMode.Restart),
        label = "spin"
    )
    val state = remember { SkyState(twinkle, spinState) }
    LaunchedEffect(Unit) {
        val rnd = Random(System.nanoTime())
        while (true) {
            delay(rnd.nextLong(5000, 14000))
            val fromLeft = rnd.nextBoolean()
            // Angle is in pixel space: near-horizontal, sloping slightly downward.
            val slope = 0.05f + rnd.nextFloat() * 0.25f
            state.angle = if (fromLeft) slope else (PI.toFloat() - slope)
            state.from = Offset(
                if (fromLeft) 0.05f + rnd.nextFloat() * 0.3f else 0.65f + rnd.nextFloat() * 0.3f,
                0.05f + rnd.nextFloat() * 0.3f
            )
            Animatable(0f).animateTo(1f, tween(1100)) { state.progress = value }
            state.progress = -1f
        }
    }
    return state
}

class SkyState(
    private val twinkleState: State<Float>,
    private val spinState: State<Float>
) {
    var progress by mutableFloatStateOf(-1f)
    var from by mutableStateOf(Offset.Zero)
    var angle by mutableFloatStateOf(0f)
    val twinkle: Float get() = twinkleState.value
    val spin: Float get() = 0.06f * sin(2 * PI * spinState.value).toFloat()
}

fun DrawScope.drawSky(state: SkyState) {
    drawStars(Stars.home, state.twinkle, state.spin)
    val p = state.progress
    if (p in 0f..1f) {
        val ux = kotlin.math.cos(state.angle)
        val uy = sin(state.angle)
        val travel = size.width * 0.6f
        val head = Offset(
            state.from.x * size.width + ux * travel * p,
            state.from.y * size.height + uy * travel * p
        )
        val trailLen = 40.dp.toPx()
        val tail = head - Offset(ux * trailLen, uy * trailLen)
        val envelope = sin(PI * p).toFloat()
        val segments = 12
        for (i in 0 until segments) {
            val s0 = i.toFloat() / segments
            val s1 = (i + 1).toFloat() / segments
            val start = tail + (head - tail) * s0
            val end = tail + (head - tail) * s1
            drawLine(
                color = Color.White.copy(alpha = envelope * s1 * s1),
                start = start,
                end = end,
                strokeWidth = 0.8.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
        drawCircle(
            color = Color.White.copy(alpha = envelope),
            radius = 1.4.dp.toPx(),
            center = head
        )
    }
}
