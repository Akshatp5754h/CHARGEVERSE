package com.akshat.chargeverse.ui.home

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.akshat.chargeverse.R
import com.akshat.chargeverse.battery.BatteryReading
import com.akshat.chargeverse.charging.ChargingActivity
import com.akshat.chargeverse.core.theme.Ink
import com.akshat.chargeverse.core.theme.NeonBlue
import com.akshat.chargeverse.core.theme.NeonPurple
import com.akshat.chargeverse.core.theme.OutlineDim
import com.akshat.chargeverse.core.theme.cvColors
import com.akshat.chargeverse.data.db.MediaItemEntity
import com.akshat.chargeverse.ui.components.CvHeroCard
import com.akshat.chargeverse.ui.components.CvIcon
import com.akshat.chargeverse.ui.components.CvMediaPoster
import com.akshat.chargeverse.ui.components.CvMediaPreview
import com.akshat.chargeverse.ui.components.CvPrimaryButton
import com.akshat.chargeverse.ui.components.CvSecondaryButton
import com.akshat.chargeverse.ui.components.EmptyState
import com.akshat.chargeverse.ui.components.GlassCard
import com.akshat.chargeverse.ui.components.SectionTitle
import com.akshat.chargeverse.ui.components.StatRow
import com.akshat.chargeverse.ui.components.cvPressScale
import com.akshat.chargeverse.ui.nav.CvDestination

private const val NOT_REPORTED = "Not reported"

/**
 * CHARGERVERSE v2/S2 — cinematic home.
 *
 * Sections: gradient wordmark, compact hero (ring + profile backdrop behind a
 * contract scrim), always-visible live stats ("Not reported" for every null —
 * D16), active profile card with inline preview, real quick actions,
 * reactive recent/discover poster rows (§6/§7) and the battery insights strip
 * (§8). Loading state while the first battery reading arrives.
 */
@Composable
fun HomeScreen(
    onNavigate: (CvDestination) -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val state by viewModel.ui.collectAsState()
    val context = LocalContext.current

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // §5: hero card ≤ 38% of screen height (36% keeps headroom).
        val heroHeight = maxHeight * 0.36f
        val topPad = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val bottomPad = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 96.dp

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = topPad + 8.dp,
                bottom = bottomPad,
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item { Wordmark() }
            item {
                CvHeroCard(
                    reading = state.battery,
                    stateLabel = state.chargingStateLabel,
                    etaLabel = state.battery?.minutesToFull?.let { "Full in ${formatMinutes(it)}" },
                    heroHeight = heroHeight,
                    backdropMedia = state.activeProfileMedia,
                )
            }
            item { LiveStatsCard(state.battery, state.chargingSourceLabel) }
            item { ActiveProfileCard(state, onNavigate) }
            item { QuickActions(state.activeProfile != null, onNavigate, context) }
            item {
                Column {
                    SectionTitle("Recent animations")
                    if (state.recent.isEmpty()) {
                        EmptyState(
                            iconRes = R.drawable.ic_util_sparkle,
                            title = "Nothing recent yet",
                            body = "Import animations to build your charging universe.",
                        )
                    } else {
                        PosterRow(state.recent, onNavigate)
                    }
                }
            }
            item {
                // §7: honest visibility — hidden entirely with < 2 library items.
                if (state.library.size >= 2 && state.discover.isNotEmpty()) {
                    Column {
                        SectionTitle("Discover")
                        PosterRow(state.discover, onNavigate)
                    }
                }
            }
            item { BatteryInsights(state.hasBatteryInsights, state.battery, onNavigate) }
        }
    }
}

/** §5 identity: NeonBlue→NeonPurple gradient wordmark + tagline. */
@Composable
private fun Wordmark() {
    val cv = MaterialTheme.cvColors
    Column {
        Text(
            text = "CHARGERVERSE",
            style = MaterialTheme.typography.titleLarge.merge(
                TextStyle(
                    brush = Brush.horizontalGradient(listOf(NeonBlue, NeonPurple)),
                    letterSpacing = 3.sp,
                ),
            ),
        )
        Text(
            text = "Your charging universe",
            style = MaterialTheme.typography.labelMedium,
            color = cv.textTertiary,
        )
    }
}

/** §3: live stats — every row always renders; nulls read "Not reported" (D16). */
@Composable
private fun LiveStatsCard(reading: BatteryReading?, sourceLabel: String) {
    val cv = MaterialTheme.cvColors
    Column {
        SectionTitle("Live stats")
        GlassCard {
            if (reading == null) {
                // Contract loading recipe while the first reading arrives.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = NeonBlue,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Waiting for the first battery reading…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cv.textSecondary,
                    )
                }
            } else {
                StatRow("Status", reading.statusLabel)
                StatRow("Source", sourceLabel)
                StatRow("Temperature", reading.temperatureC?.let { "%.1f °C".format(it) } ?: NOT_REPORTED)
                StatRow("Charging power", reading.chargingPowerMw?.let { "%.1f W".format(it / 1000.0) } ?: NOT_REPORTED)
                StatRow("Time to full", reading.minutesToFull?.let { formatMinutes(it) } ?: NOT_REPORTED)
                StatRow("Voltage", reading.voltageMv?.let { "%d mV".format(it) } ?: NOT_REPORTED)
                StatRow("Current", reading.currentMicroAmp?.let { "%.1f mA".format(it / 1000.0) } ?: NOT_REPORTED)
            }
        }
    }
}

/** §5: active profile card — "Set as active" state, empty state, inline preview. */
@Composable
private fun ActiveProfileCard(
    state: HomeUiState,
    onNavigate: (CvDestination) -> Unit,
) {
    val cv = MaterialTheme.cvColors
    // Reset the preview whenever the active profile changes.
    var previewing by remember(state.activeProfile?.id) { mutableStateOf(false) }

    Column {
        SectionTitle("Charging experience")
        GlassCard(glow = true) {
            Text(
                "ACTIVE PROFILE",
                style = MaterialTheme.typography.labelMedium,
                color = cv.textTertiary,
            )
            Spacer(Modifier.height(6.dp))

            val profile = state.activeProfile
            val media = state.activeProfileMedia
            if (profile == null) {
                EmptyState(
                    iconRes = R.drawable.ic_nav_studio,
                    title = "No profile set",
                    body = "Build one in Studio from your animations — it will run whenever you charge.",
                ) {
                    CvPrimaryButton("Open Studio") { onNavigate(CvDestination.STUDIO) }
                }
            } else {
                Text(
                    profile.name.ifBlank { "Profile" },
                    style = MaterialTheme.typography.titleLarge,
                    color = cv.textPrimary,
                )
                Spacer(Modifier.height(4.dp))
                // Visible text state — never colour-only (§5).
                Text(
                    "Active — the charging screen plays this profile",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cv.textSecondary,
                )
                if (media == null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Its animation is missing — recreate the profile in Studio.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cv.textTertiary,
                    )
                } else {
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(96.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Ink)
                                .border(1.dp, OutlineDim.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                .cvPressScale { previewing = !previewing }
                                .semantics {
                                    contentDescription =
                                        if (previewing) "Stop preview of ${media.title}"
                                        else "Preview animation: ${media.title}"
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (previewing) {
                                CvMediaPreview(
                                    media = media,
                                    modifier = Modifier.fillMaxSize(),
                                    loop = state.settings.loopAnimation,
                                )
                                Box(
                                    Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(6.dp)
                                        .size(24.dp)
                                        .background(Color(0xCC000000), CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CvIcon(R.drawable.ic_util_close, cv.textPrimary, Modifier.size(12.dp))
                                }
                            } else {
                                CvMediaPoster(media, Modifier.fillMaxSize())
                                // Play affordance over the poster; 0xCC circle keeps
                                // worst-case contrast above the 3:1 graphic floor.
                                Box(
                                    Modifier
                                        .size(40.dp)
                                        .background(Color(0xCC000000), CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CvIcon(R.drawable.ic_util_play, cv.textPrimary, Modifier.size(18.dp))
                                }
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                media.title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = cv.textSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                if (previewing) "Previewing — tap to stop" else "Tap to preview inline",
                                style = MaterialTheme.typography.labelMedium,
                                color = cv.textTertiary,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** §5: quick actions — real buttons only; preview exists only with a profile. */
@Composable
private fun QuickActions(
    hasProfile: Boolean,
    onNavigate: (CvDestination) -> Unit,
    context: android.content.Context,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (hasProfile) {
            CvPrimaryButton("Open charging preview") {
                context.startActivity(Intent(context, ChargingActivity::class.java))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CvSecondaryButton("Animations", Modifier.weight(1f)) { onNavigate(CvDestination.ANIMATIONS) }
            CvSecondaryButton("Sound studio", Modifier.weight(1f)) { onNavigate(CvDestination.SOUNDS) }
        }
    }
}

/** §6/§7: poster rows; tap goes to the Animations library. */
@Composable
private fun PosterRow(
    items: List<MediaItemEntity>,
    onNavigate: (CvDestination) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 4.dp),
    ) {
        items(items, key = { it.id }) { item ->
            PosterTile(item) { onNavigate(CvDestination.ANIMATIONS) }
        }
    }
}

@Composable
private fun PosterTile(item: MediaItemEntity, onClick: () -> Unit) {
    val cv = MaterialTheme.cvColors
    Box(
        Modifier
            .size(width = 104.dp, height = 140.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Ink)
            .border(1.dp, OutlineDim.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .semantics { contentDescription = "Animation preview: ${item.title}" }
            .cvPressScale(onClick = onClick),
    ) {
        CvMediaPoster(item, Modifier.fillMaxSize())
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color(0xE6000000))
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            Text(
                item.title,
                style = MaterialTheme.typography.labelLarge,
                color = cv.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** §8: three real telemetry cells, else a single honest hint row to Battery. */
@Composable
private fun BatteryInsights(
    hasInsights: Boolean,
    reading: BatteryReading?,
    onNavigate: (CvDestination) -> Unit,
) {
    val cv = MaterialTheme.cvColors
    Column {
        SectionTitle("Battery insights")
        if (hasInsights && reading != null) {
            GlassCard {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    InsightCell("Cycles", reading.cycleCount?.toString(), Modifier.weight(1f))
                    InsightCell(
                        "Charge level",
                        reading.chargeCounterUah?.let { "%.0f mAh".format(it / 1000.0) },
                        Modifier.weight(1f),
                    )
                    InsightCell(
                        "Current",
                        reading.currentMicroAmp?.let { "%.1f mA".format(it / 1000.0) },
                        Modifier.weight(1f),
                    )
                }
            }
        } else {
            GlassCard {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .cvPressScale { onNavigate(CvDestination.BATTERY) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CvIcon(R.drawable.ic_nav_battery, NeonBlue, Modifier.size(18.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Detailed telemetry in Battery",
                        style = MaterialTheme.typography.bodyLarge,
                        color = cv.textSecondary,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun InsightCell(label: String, value: String?, modifier: Modifier = Modifier) {
    val cv = MaterialTheme.cvColors
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = cv.textTertiary)
        Spacer(Modifier.height(2.dp))
        Text(
            value ?: NOT_REPORTED,
            style = MaterialTheme.typography.titleMedium,
            color = if (value != null) cv.textPrimary else cv.textTertiary,
        )
    }
}

private fun formatMinutes(minutes: Long): String = when {
    minutes >= 60 -> "${minutes / 60}h ${minutes % 60}m"
    else -> "${minutes}m"
}
