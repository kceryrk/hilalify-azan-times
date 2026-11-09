package com.kceryrk.prayertimes

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** The activity's background layer, so dialogs can blur what is behind them like the nav bar does. */
val LocalGlassBackdrop = compositionLocalOf<GraphicsLayer?> { null }

/** Root-level slot where glass dialogs are drawn above the blurred content. */
val LocalGlassDialogSlot = compositionLocalOf<MutableState<(@Composable () -> Unit)?>?> { null }

@Composable
fun GlassSearchField(query: String, onQueryChange: (String) -> Unit, placeholder: String) {
    var focused by remember { mutableStateOf(false) }
    val accent = MaterialTheme.colorScheme.primary
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(accent),
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .onFocusChanged { focused = it.isFocused }
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .border(if (focused) 2.dp else 1.dp, if (focused) accent else Color.Gray.copy(alpha = 0.5f), CircleShape)
            .padding(horizontal = 16.dp),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                }
                inner()
            }
        }
    )
}

/**
 * Liquid-glass dialog with the same look as the nav bar: the content behind is blurred and refracted
 * by [GlassLens] (Android 13+). Inside the app it is drawn in a root overlay; otherwise it falls back to a Dialog.
 * Slots mirror [androidx.compose.material3.AlertDialog] so it can replace it directly.
 */
// Plain text action without Material's 40dp minimum touch height, so it doesn't inflate the card.
@Composable
fun GlassDialogAction(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

@Composable
fun GlassAlertDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null
) {
    val backdrop = LocalGlassBackdrop.current
    val slot = LocalGlassDialogSlot.current
    val card: @Composable () -> Unit = {
        GlassDialogCard(onDismissRequest, backdrop, title, text, confirmButton, dismissButton)
    }
    if (slot != null) {
        SideEffect { slot.value = card }
        DisposableEffect(slot) { onDispose { slot.value = null } }
    } else {
        Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            card()
        }
    }
}

@Composable
private fun GlassDialogCard(
    onDismiss: () -> Unit,
    backdrop: GraphicsLayer?,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)?
) {
    val density = LocalDensity.current
    val lens = remember { if (Build.VERSION.SDK_INT >= 33) GlassLens() else null }
    val bgColor = MaterialTheme.colorScheme.background
    val tint = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
    val shape = RoundedCornerShape(28.dp)
    val noRipple = remember { MutableInteractionSource() }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var pos by remember { mutableStateOf(Offset.Zero) }
    BackHandler(onBack = onDismiss)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.25f))
            .clickable(interactionSource = noRipple, indication = null, onClick = onDismiss)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .onSizeChanged { size = it }
                .onGloballyPositioned { pos = it.positionInRoot() }
                // Consumes taps on the card so they don't reach the scrim and dismiss the dialog.
                .clickable(interactionSource = noRipple, indication = null) {}
        ) {
            Box(
                Modifier
                    .matchParentSize()
                    .then(
                        if (lens != null && size.width > 0) Modifier.graphicsLayer {
                            clip = true
                            this.shape = shape
                            renderEffect = lens.effectFor(
                                size.width / 2f, size.height / 2f, size.width / 2f, size.height / 2f,
                                with(density) { 28.dp.toPx() }, with(density) { 24.dp.toPx() },
                                with(density) { 22.dp.toPx() }, with(density) { 2.dp.toPx() }
                            )
                        } else Modifier.clip(shape)
                    )
                    .drawBehind {
                        drawRect(bgColor)
                        if (backdrop != null) withTransform({ translate(-pos.x, -pos.y) }) { drawLayer(backdrop) }
                        drawRect(tint)
                    }
            )
            // Separate overlay so the lens render effect cannot hide the border or rim.
            Box(
                Modifier
                    .matchParentSize()
                    .drawWithContent {
                        drawContent()
                        drawRoundRect(
                            color = Color.Gray.copy(alpha = 0.5f),
                            cornerRadius = CornerRadius(28.dp.toPx()),
                            style = Stroke(0.5.dp.toPx())
                        )
                        // Light top edge, like light catching the rim of glass.
                        drawRoundRect(
                            brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Transparent)),
                            cornerRadius = CornerRadius(28.dp.toPx()),
                            style = Stroke(0.5.dp.toPx())
                        )
                    }
            )
            Column(Modifier.padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 12.dp)) {
                ProvideTextStyle(MaterialTheme.typography.headlineSmall) { title() }
                Spacer(Modifier.height(16.dp))
                ProvideTextStyle(MaterialTheme.typography.bodyMedium) { text() }
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    dismissButton?.invoke()
                    if (dismissButton != null) Spacer(Modifier.width(8.dp))
                    confirmButton()
                }
            }
        }
    }
}
