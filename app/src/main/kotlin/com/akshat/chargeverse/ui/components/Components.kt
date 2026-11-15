package com.akshat.chargeverse.ui.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.akshat.chargeverse.core.theme.TextPrimary
import com.akshat.chargeverse.core.theme.GlassFill
import com.akshat.chargeverse.core.theme.GlassStroke
import com.akshat.chargeverse.core.theme.NeonBlue
import com.akshat.chargeverse.core.theme.NeonPurple
import com.akshat.chargeverse.core.theme.DimText
import com.akshat.chargeverse.core.theme.OutlineDim
import com.akshat.chargeverse.core.theme.cvColors

/** Glass panel used across the app: dark fill, subtle stroke, glow accent. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    glow: Boolean = false,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier) {
        if (glow) {
            Box(
                Modifier
                    .matchParentSize()
                    .blur(18.dp)
                    .background(
                        Brush.linearGradient(
                            listOf(NeonBlue.copy(alpha = 0.18f), NeonPurple.copy(alpha = 0.18f))
                        )
                    )
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .background(GlassFill, RoundedCornerShape(24.dp))
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(GlassStroke, RoundedCornerShape(24.dp))
            )
            Column(Modifier.padding(20.dp)) { content() }
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = DimText,
        modifier = modifier.padding(horizontal = 4.dp, vertical = 8.dp),
    )
}

/** Icon rendered from the app's own vector drawables with a tint. */
@Composable
fun CvIcon(resId: Int, tint: Color, modifier: Modifier = Modifier) {
    Icon(
        painter = painterResource(resId),
        contentDescription = null,
        tint = tint,
        modifier = modifier,
    )
}

@Composable
fun StatRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = DimText)
        Spacer(Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
    }
}

@Composable
fun EmptyState(
    iconRes: Int,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    button: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Contract §3: icon inside a 64.dp OutlineDim-tinted circle.
        Box(
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(OutlineDim.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            CvIcon(iconRes, MaterialTheme.cvColors.textTertiary, Modifier.size(28.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.cvColors.textTertiary,
            textAlign = TextAlign.Center,
        )
        button?.let { action ->
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}

// ---- v2/S2 contract buttons & press motion (§3/§4) --------------------------

/** Contract primary button: gradient fill, OnAccent label, ≥48dp tall. */
@Composable
fun CvPrimaryButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.cvColors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .graphicsLayer {
                val scale = if (pressed) 0.97f else 1f
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(listOf(colors.btnPrimaryStart, colors.btnPrimaryEnd)),
                RoundedCornerShape(16.dp),
            )
            .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = colors.onAccent)
    }
}

/** Contract secondary button: transparent fill, OutlineDim border, NeonBlue label. */
@Composable
fun CvSecondaryButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier
            .heightIn(min = 48.dp)
            .graphicsLayer {
                val scale = if (pressed) 0.97f else 1f
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Transparent, RoundedCornerShape(16.dp))
            .border(1.dp, OutlineDim, RoundedCornerShape(16.dp))
            .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = NeonBlue)
    }
}

/**
 * §4 tap motion: cards scale to 0.97 while pressed (never ripple-only).
 * Combines the press scale and the click in one modifier so callers keep a
 * single chain.
 */
@Composable
fun Modifier.cvPressScale(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    return this
        .graphicsLayer {
            val scale = if (pressed) 0.97f else 1f
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interaction,
            indication = LocalIndication.current,
            enabled = enabled,
            onClick = onClick,
        )
}
