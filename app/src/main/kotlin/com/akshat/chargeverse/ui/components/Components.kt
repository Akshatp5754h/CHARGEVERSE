package com.akshat.chargeverse.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.akshat.chargeverse.core.theme.TextPrimary
import com.akshat.chargeverse.core.theme.GlassFill
import com.akshat.chargeverse.core.theme.GlassStroke
import com.akshat.chargeverse.core.theme.NeonBlue
import com.akshat.chargeverse.core.theme.NeonPurple
import com.akshat.chargeverse.core.theme.DimText

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
fun EmptyState(iconRes: Int, title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CvIcon(iconRes, DimText, Modifier.size(40.dp))
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = DimText)
    }
}
