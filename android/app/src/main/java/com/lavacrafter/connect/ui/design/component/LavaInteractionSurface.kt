// SPDX-License-Identifier: Apache-2.0
// Adapted from lava_note (Copyright 2026 Muchen Jiang), ui/design.
package com.lavacrafter.connect.ui.design.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lavacrafter.connect.ui.design.LavaInteraction
import com.lavacrafter.connect.ui.design.LavaSpacing
import com.lavacrafter.connect.ui.design.LocalLavaMotion

@Composable
internal fun LavaInteractiveSurface(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    selectionSemantics: Boolean = false,
    contentDescription: String? = null,
    onClick: (() -> Unit)? = null,
    shape: Shape = MaterialTheme.shapes.small,
    minWidth: Dp = Dp.Unspecified,
    minHeight: Dp = Dp.Unspecified,
    containerColor: Color = Color.Transparent,
    contentColor: Color? = null,
    contentPadding: PaddingValues = PaddingValues(LavaSpacing.small),
    visualInset: Dp = 0.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()
    var isFocused by remember { mutableStateOf(false) }

    val colorScheme = MaterialTheme.colorScheme
    val selectedColor = colorScheme.primaryContainer
    val baseColor = if (selected) selectedColor else containerColor
    val targetColor =
        when {
            isPressed -> baseColor.withInteractionOverlay(colorScheme.onSurface, LavaInteraction.defaults.pressedContainerAlpha)
            isHovered -> baseColor.withInteractionOverlay(colorScheme.onSurface, LavaInteraction.defaults.hoveredContainerAlpha)
            else -> baseColor
        }
    val animatedColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(LocalLavaMotion.current.microMillis),
        label = "lavaInteractionContainer",
    )
    val resolvedContentColor =
        contentColor ?: when {
            selected -> colorScheme.onPrimaryContainer
            else -> colorScheme.onSurface
        }
    val borderColor =
        when {
            isFocused -> colorScheme.primary.copy(alpha = LavaInteraction.defaults.focusedBorderAlpha)
            selected -> colorScheme.primary.copy(alpha = LavaInteraction.defaults.focusedBorderAlpha)
            else -> Color.Transparent
        }
    val borderWidth = if (borderColor != Color.Transparent) 1.dp else 0.dp

    val interactionModifier =
        Modifier
            .onFocusChanged { isFocused = it.isFocused }
            .hoverable(interactionSource)
            .let { current ->
                if (onClick == null) {
                    current
                } else {
                    current
                        .focusable(enabled = enabled, interactionSource = interactionSource)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            enabled = enabled,
                            role = Role.Button,
                            onClick = onClick,
                        )
                }
            }

    Box(
        modifier =
            modifier
                .sizeIn(minWidth = minWidth, minHeight = minHeight)
                .then(interactionModifier)
                .semantics(mergeDescendants = true) {
                    if (contentDescription != null) {
                        this.contentDescription = contentDescription
                    }
                    if (selectionSemantics) {
                        this.selected = selected
                    }
                    if (onClick != null) {
                        this.role = Role.Button
                    }
                },
        contentAlignment = Alignment.Center,
        content = {
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .padding(visualInset)
                        .background(animatedColor, shape)
                        .border(borderWidth, borderColor, shape),
            )
            Box(
                modifier =
                    Modifier
                        .padding(contentPadding),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalContentColor provides resolvedContentColor,
                ) { content() }
            }
        },
    )
}

private fun Color.withInteractionOverlay(
    overlay: Color,
    alpha: Float,
): Color = lerp(this, overlay, alpha)

/**
 * Applies the same high-contrast focus treatment used by Lava's interaction surfaces to custom
 * gesture/click surfaces. The modifier deliberately does not create a focus target: callers that
 * are not backed by [clickable] should add [focusable] themselves so pointer-only surfaces do not
 * accidentally become keyboard actions.
 */
fun Modifier.lavaFocusIndicator(enabled: Boolean = true): Modifier =
    composed {
        var isFocused by remember { mutableStateOf(false) }
        val focusColor = MaterialTheme.colorScheme.primary
        val focusAlpha = LavaInteraction.defaults.focusedBorderAlpha

        this
            .onFocusChanged { isFocused = it.isFocused }
            .drawWithContent {
                drawContent()
                if (enabled && isFocused) {
                    drawRect(
                        color = focusColor.copy(alpha = focusAlpha),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
            }
    }
