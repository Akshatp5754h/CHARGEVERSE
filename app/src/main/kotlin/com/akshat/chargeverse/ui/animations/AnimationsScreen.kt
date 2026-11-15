package com.akshat.chargeverse.ui.animations

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.akshat.chargeverse.R
import com.akshat.chargeverse.core.theme.Ink
import com.akshat.chargeverse.core.theme.NeonBlue
import com.akshat.chargeverse.core.theme.OutlineDim
import com.akshat.chargeverse.core.theme.Surface
import com.akshat.chargeverse.core.theme.Void
import com.akshat.chargeverse.core.theme.cvColors
import com.akshat.chargeverse.data.db.AnimationCategory
import com.akshat.chargeverse.data.db.MediaItemEntity
import com.akshat.chargeverse.data.db.MediaType
import com.akshat.chargeverse.ui.components.CvMediaPoster
import com.akshat.chargeverse.ui.components.CvPrimaryButton
import com.akshat.chargeverse.ui.components.CvSecondaryButton
import com.akshat.chargeverse.ui.components.ConfirmDeleteDialog
import com.akshat.chargeverse.ui.components.EmptyState
import com.akshat.chargeverse.ui.components.StatRow
import com.akshat.chargeverse.ui.components.cvPressScale
import java.text.DateFormat
import java.util.Date

/**
 * S3 animation library (directive §6): header with search, filter chips with
 * counts, sort menu, 2-column media grid and a state-based detail overlay
 * with a fit/fill video preview. Contract tokens live in core/theme; the
 * only raw colour permitted is the §1 text-bearing scrim (70% black).
 */
private val Scrim70 = Color(0xB3000000)

@Composable
fun AnimationsScreen(viewModel: AnimationsViewModel = viewModel()) {
    val state by viewModel.ui.collectAsState()
    var deleteCandidate by remember { mutableStateOf<MediaItemEntity?>(null) }
    var selected by remember { mutableStateOf<MediaItemEntity?>(null) }
    var searchOpen by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.stageImport(it) }
    }
    val launchImport = {
        picker.launch(arrayOf("video/*", "image/*"))
    }

    // S3: the detail overlay closes on system back too.
    val selectedItem = selected
    BackHandler(enabled = selectedItem != null) {
        selected = null
        viewModel.clearProfileHint()
    }

    Box(Modifier.fillMaxSize().background(Void)) {
        Column(Modifier.fillMaxSize()) {
            Header(
                state = state,
                searchOpen = searchOpen,
                onToggleSearch = {
                    searchOpen = !searchOpen
                    if (!searchOpen) viewModel.setQuery("")
                },
                onQuery = viewModel::setQuery,
            )
            Spacer(Modifier.height(14.dp))
            CategoryChips(state, onSelect = viewModel::selectCategory)
            SortRow(state.sort, onSort = viewModel::setSort)

            // D18: the importing state is now rendered (progress + label)…
            if (state.importing) {
                ImportingRow()
            }
            // …and the error banner is dismissible with X (wired to dismissError).
            state.error?.let { err ->
                ErrorBanner(err, onDismiss = viewModel::dismissError)
            }

            val hasFilters = state.query.isNotBlank() || state.category != null
            if (state.items.isEmpty()) {
                if (hasFilters) {
                    EmptyState(
                        iconRes = R.drawable.ic_util_sparkle,
                        title = "No matches",
                        body = "Nothing matches your search or filter. Clear them to see the whole library.",
                        button = {
                            CvSecondaryButton("Clear filters") {
                                viewModel.selectCategory(null)
                                viewModel.setQuery("")
                            }
                        },
                    )
                } else {
                    EmptyState(
                        iconRes = R.drawable.ic_util_sparkle,
                        title = "No animations yet",
                        body = "Import videos, GIFs, WebP or images to build your charging universe.",
                        button = {
                            CvPrimaryButton(
                                "Import your first animation",
                                modifier = Modifier.padding(horizontal = 56.dp),
                                onClick = launchImport,
                            )
                        },
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(
                        start = 20.dp, end = 20.dp, top = 10.dp, bottom = 140.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.items, key = { it.id }) { item ->
                        MediaTile(
                            item = item,
                            onOpen = { selected = item },
                            onDelete = { deleteCandidate = item },
                        )
                    }
                }
            }
        }

        // Floating import action; hidden while an import is running.
        if (!state.importing) {
            ImportFab(
                onClick = launchImport,
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }

        // S3 detail overlay: full-bleed preview + metadata + actions. The
        // key() ensures the previous overlay (and its ExoPlayer) is fully
        // disposed before the next media item's preview is composed.
        selectedItem?.let { media ->
            key(media.id) {
                DetailOverlay(
                    media = media,
                    profileCreated = state.profileCreatedFor == media.id,
                    onCreateProfile = { viewModel.createProfile(media) },
                    onClose = {
                        selected = null
                        viewModel.clearProfileHint()
                    },
                    onDelete = {
                        selected = null
                        viewModel.clearProfileHint()
                        deleteCandidate = media
                    },
                )
            }
        }
    }

    // Category chooser for a freshly picked file (restyled per contract).
    val pending = state.pendingUri
    if (pending != null) {
        CategoryChooserDialog(
            onPick = { viewModel.import(pending, it) },
            onCancel = { viewModel.cancelImport() },
        )
    }

    // D9: deleting an animation also removes its imported file — confirm first.
    deleteCandidate?.let { item ->
        ConfirmDeleteDialog(
            title = "Delete animation",
            message = "“${item.title}” and its imported file will be removed from your library. Charging profiles that use it are deleted too.",
            onConfirm = {
                viewModel.delete(item)
                deleteCandidate = null
            },
            onDismiss = { deleteCandidate = null },
        )
    }
}

// ---- Header: title + count + search -----------------------------------------

@Composable
private fun Header(
    state: AnimationsUiState,
    searchOpen: Boolean,
    onToggleSearch: () -> Unit,
    onQuery: (String) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 56.dp)
            .padding(horizontal = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Animations",
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.cvColors.textPrimary,
                )
                Text(
                    if (state.query.isBlank()) "${state.totalCount} in library"
                    else "${state.items.size} of ${state.totalCount} shown",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.cvColors.textSecondary,
                )
            }
            IconButton(
                onClick = onToggleSearch,
                modifier = Modifier.semantics {
                    contentDescription = if (searchOpen) "Close search" else "Search animations"
                },
            ) {
                if (searchOpen) {
                    Icon(
                        painter = painterResource(R.drawable.ic_util_close),
                        contentDescription = null,
                        tint = MaterialTheme.cvColors.textSecondary,
                        modifier = Modifier.size(22.dp),
                    )
                } else {
                    SearchGlyph(NeonBlue, Modifier.size(22.dp))
                }
            }
        }
        if (searchOpen) {
            Spacer(Modifier.height(8.dp))
            SearchField(state.query, onQuery)
        }
    }
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Surface)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SearchGlyph(MaterialTheme.cvColors.textTertiary, Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text(
                    "Search title or category",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.cvColors.textTertiary,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.cvColors.textPrimary,
                ),
                cursorBrush = SolidColor(NeonBlue),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 13.dp),
            )
        }
        if (query.isNotEmpty()) {
            IconButton(onClick = { onQuery("") }) {
                Icon(
                    painter = painterResource(R.drawable.ic_util_close),
                    contentDescription = "Clear search",
                    tint = MaterialTheme.cvColors.textSecondary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/** Small search glyph (this app ships no material-icons artifact). */
@Composable
private fun SearchGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val d = size.minDimension
        val stroke = Stroke(width = d * 0.10f, cap = StrokeCap.Round)
        val radius = d * 0.30f
        val center = Offset(d * 0.40f, d * 0.40f)
        drawCircle(color = tint, radius = radius, center = center, style = stroke)
        drawLine(
            color = tint,
            start = center + Offset(radius, radius) * 0.7071f,
            end = Offset(d * 0.88f, d * 0.88f),
            strokeWidth = stroke.width,
            cap = StrokeCap.Round,
        )
    }
}

/** Alert/exclamation glyph for the error banner (§3 error recipe). */
@Composable
private fun AlertGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val d = size.minDimension
        val stroke = Stroke(width = d * 0.09f, cap = StrokeCap.Round)
        drawCircle(
            color = tint,
            radius = d / 2f - stroke.width,
            center = Offset(d / 2f, d / 2f),
            style = stroke,
        )
        drawLine(
            color = tint,
            start = Offset(d / 2f, d * 0.26f),
            end = Offset(d / 2f, d * 0.58f),
            strokeWidth = stroke.width,
            cap = StrokeCap.Round,
        )
        drawCircle(color = tint, radius = stroke.width * 0.8f, center = Offset(d / 2f, d * 0.75f))
    }
}

// ---- Filter chips & sort menu ------------------------------------------------

@Composable
private fun CategoryChips(
    state: AnimationsUiState,
    onSelect: (AnimationCategory?) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            label = "All · ${state.totalCount}",
            selected = state.category == null,
            onClick = { onSelect(null) },
        )
        AnimationCategory.entries.forEach { cat ->
            FilterChip(
                label = "${cat.name.lowercase().replace('_', ' ')} · ${state.counts[cat] ?: 0}",
                selected = state.category == cat,
                onClick = { onSelect(cat) },
            )
        }
    }
}

/** Contract chip: Surface fill, 12.dp radius; selected = NeonBlue + OnAccent. */
@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) NeonBlue else Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selected) {
                Icon(
                    painter = painterResource(R.drawable.ic_util_check),
                    contentDescription = null,
                    tint = MaterialTheme.cvColors.onAccent,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(5.dp))
            }
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.cvColors.onAccent
                else MaterialTheme.cvColors.textSecondary,
            )
        }
    }
}

@Composable
private fun SortRow(sort: AnimationsSort, onSort: (AnimationsSort) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        Box {
            Row(
                Modifier
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, OutlineDim, RoundedCornerShape(12.dp))
                    .clickable { expanded = true }
                    .padding(horizontal = 16.dp)
                    .semantics { contentDescription = "Change sort order" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Sort: ${sort.label}",
                    style = MaterialTheme.typography.labelLarge,
                    color = NeonBlue,
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                AnimationsSort.entries.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                option.label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.cvColors.textPrimary,
                            )
                        },
                        leadingIcon = if (option == sort) {
                            {
                                Icon(
                                    painter = painterResource(R.drawable.ic_util_check),
                                    contentDescription = null,
                                    tint = NeonBlue,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        } else null,
                        onClick = {
                            onSort(option)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

// ---- D18: import progress + dismissible error --------------------------------

@Composable
private fun ImportingRow() {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LinearProgressIndicator(
            modifier = Modifier.weight(1f),
            color = NeonBlue,
            trackColor = OutlineDim,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            "Importing…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.cvColors.textSecondary,
        )
    }
}

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
            .padding(start = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AlertGlyph(MaterialTheme.cvColors.error, Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.cvColors.textPrimary,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 12.dp),
        )
        IconButton(onClick = onDismiss) {
            Icon(
                painter = painterResource(R.drawable.ic_util_close),
                contentDescription = "Dismiss error",
                tint = MaterialTheme.cvColors.textSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

// ---- Grid tiles ---------------------------------------------------------------

@Composable
private fun MediaTile(item: MediaItemEntity, onOpen: () -> Unit, onDelete: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            // §4: scale 0.97 while pressed; graphicsLayer first so the whole
            // card (fill, border, poster) scales, not just its content.
            .cvPressScale(onClick = onOpen)
            .clip(RoundedCornerShape(24.dp))
            .background(Ink)
            .border(1.dp, OutlineDim.copy(alpha = 0.4f), RoundedCornerShape(24.dp)),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.9f)
                .background(Void),
        ) {
            CvMediaPoster(
                media = item,
                contentDesc = "Animation preview: ${item.title}",
                modifier = Modifier.fillMaxSize(),
            )
            TypeBadge(item.type, Modifier.align(Alignment.TopStart).padding(8.dp))
            if (item.durationMs > 0) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .background(Scrim70, RoundedCornerShape(8.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                ) {
                    Text(
                        formatDuration(item.durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.cvColors.textPrimary,
                    )
                }
            }
        }
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.cvColors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    formatSize(item.sizeBytes),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.cvColors.textTertiary,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    painter = painterResource(R.drawable.ic_util_trash),
                    contentDescription = "Delete ${item.title}",
                    tint = MaterialTheme.cvColors.textSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun TypeBadge(type: MediaType, modifier: Modifier = Modifier) {
    Box(
        modifier
            .background(Scrim70, RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        Text(
            type.name.lowercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.cvColors.textPrimary,
        )
    }
}

// ---- Floating import button ----------------------------------------------------

@Composable
private fun ImportFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(
        modifier
            .padding(end = 20.dp, bottom = navInset + 72.dp)
            .size(56.dp)
            .cvPressScale(onClick = onClick)
            .clip(CircleShape)
            .semantics { contentDescription = "Import animation" },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.cvColors.btnPrimaryStart,
                            MaterialTheme.cvColors.btnPrimaryEnd,
                        )
                    )
                ),
        )
        Icon(
            painter = painterResource(R.drawable.ic_util_plus),
            contentDescription = null,
            tint = MaterialTheme.cvColors.onAccent,
            modifier = Modifier.size(24.dp),
        )
    }
}

// ---- Detail overlay --------------------------------------------------------------

@Composable
private fun DetailOverlay(
    media: MediaItemEntity,
    profileCreated: Boolean,
    onCreateProfile: () -> Unit,
    onClose: () -> Unit,
    onDelete: () -> Unit,
) {
    // true = RESIZE_MODE_ZOOM (fill), false = RESIZE_MODE_FIT (letterbox).
    var zoom by remember { mutableStateOf(true) }

    Box(Modifier.fillMaxSize().background(Void)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 56.dp)
                    .height(340.dp)
                    .background(Void),
            ) {
                OverlayMediaPreview(
                    media = media,
                    zoom = zoom,
                    modifier = Modifier.fillMaxSize(),
                )
                if (media.type == MediaType.VIDEO) {
                    Row(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PreviewScalePill(
                            label = "Fill",
                            isSelected = zoom,
                            desc = "Preview scaling: fill, crops the video to the screen",
                            onClick = { zoom = true },
                        )
                        PreviewScalePill(
                            label = "Fit",
                            isSelected = !zoom,
                            desc = "Preview scaling: fit, letterboxes the video",
                            onClick = { zoom = false },
                        )
                    }
                }
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(48.dp)
                        .background(Scrim70, CircleShape)
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_util_close),
                        contentDescription = "Close preview",
                        tint = MaterialTheme.cvColors.textPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Column(Modifier.padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(16.dp))
                Text(
                    media.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.cvColors.textPrimary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    categoryLabel(media.category),
                    style = MaterialTheme.typography.labelLarge,
                    color = NeonBlue,
                )
                Spacer(Modifier.height(12.dp))
                StatRow("Type", media.type.name.lowercase())
                StatRow("Resolution", formatResolution(media))
                StatRow(
                    "Duration",
                    if (media.durationMs > 0) formatDuration(media.durationMs) else "Not reported",
                )
                StatRow("Size", formatSize(media.sizeBytes))
                StatRow("Date added", formatDate(media.addedAtMs))
                Spacer(Modifier.height(20.dp))
                CvPrimaryButton("Use in new profile", onClick = onCreateProfile)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (profileCreated) {
                        Icon(
                            painter = painterResource(R.drawable.ic_util_check),
                            contentDescription = null,
                            tint = MaterialTheme.cvColors.success,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Profile created — edit it in Studio",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.cvColors.success,
                        )
                    } else {
                        // The profile editor is Studio-owned state.
                        Text(
                            "Edit profiles in Studio",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.cvColors.textTertiary,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                CvSecondaryButton("Delete animation", onClick = onDelete)
                Spacer(Modifier.height(140.dp))
            }
        }
    }
}

@Composable
private fun PreviewScalePill(
    label: String,
    isSelected: Boolean,
    desc: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .background(
                if (isSelected) NeonBlue else Scrim70,
                RoundedCornerShape(999.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp)
            .semantics {
                contentDescription = desc
                selected = isSelected
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (isSelected) MaterialTheme.cvColors.onAccent
            else MaterialTheme.cvColors.textPrimary,
        )
    }
}

/**
 * Full-bleed preview for the overlay:
 * - VIDEO: one ExoPlayer, muted + looping, resizeMode switches between
 *   RESIZE_MODE_ZOOM (fill) and RESIZE_MODE_FIT via the [zoom] toggle.
 * - GIF / animated WEBP: Coil animates them through the app-wide
 *   ImageLoader (ImageDecoderDecoder, registered in ChargeverseApp).
 * - IMAGE: plain Coil, fit.
 * - LOTTIE: lottie-compose, looping.
 */
@Composable
private fun OverlayMediaPreview(
    media: MediaItemEntity,
    zoom: Boolean,
    modifier: Modifier = Modifier,
) {
    when (media.type) {
        MediaType.VIDEO -> OverlayVideoPreview(media, zoom, modifier)
        MediaType.LOTTIE -> OverlayLottiePreview(media, modifier)
        MediaType.GIF, MediaType.WEBP, MediaType.IMAGE ->
            OverlayStillPreview(media, modifier)
    }
}

@Composable
private fun OverlayVideoPreview(
    media: MediaItemEntity,
    zoom: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // Single player for the overlay; released on dispose (no codec leaks).
    val player = remember(media.uri) {
        ExoPlayer.Builder(context).build().apply {
            volume = 0f
            repeatMode = Player.REPEAT_MODE_ONE
            setMediaItem(MediaItem.fromUri(media.uri))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(media.uri) {
        onDispose { player.release() }
    }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                useController = false
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                this.player = player
            }
        },
        update = { view ->
            if (view.player !== player) view.player = player
            view.resizeMode = if (zoom) {
                AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            } else {
                AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
        },
        modifier = modifier.semantics {
            contentDescription = "Animation preview: ${media.title}"
        },
    )
}

@Composable
private fun OverlayStillPreview(media: MediaItemEntity, modifier: Modifier = Modifier) {
    AsyncImage(
        model = media.uri,
        contentDescription = "Animation preview: ${media.title}",
        contentScale = ContentScale.Fit,
        modifier = modifier,
    )
}

@Composable
private fun OverlayLottiePreview(media: MediaItemEntity, modifier: Modifier = Modifier) {
    val spec = remember(media.uri) {
        val path = Uri.parse(media.uri).path
        if (media.uri.startsWith("file://") && path != null) {
            LottieCompositionSpec.File(path)
        } else {
            LottieCompositionSpec.Url(media.uri)
        }
    }
    val composition by rememberLottieComposition(spec)
    LottieAnimation(
        composition = composition,
        isPlaying = true,
        iterations = LottieConstants.IterateForever,
        contentScale = ContentScale.Fit,
        modifier = modifier.semantics {
            contentDescription = "Animation preview: ${media.title}"
        },
    )
}

// ---- Restyled category chooser (D-batch flow, contract look) ---------------------

@Composable
private fun CategoryChooserDialog(
    onPick: (AnimationCategory) -> Unit,
    onCancel: () -> Unit,
) {
    var chosen by remember { mutableStateOf<AnimationCategory?>(null) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(
                "Choose a category",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.cvColors.textPrimary,
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Where should this animation live in your library?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.cvColors.textSecondary,
                )
                Spacer(Modifier.height(12.dp))
                AnimationCategory.entries.chunked(2).forEach { pair ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        pair.forEach { cat ->
                            val isSelected = chosen == cat
                            Box(
                                Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) NeonBlue else Surface)
                                    .clickable { chosen = cat }
                                    .padding(horizontal = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isSelected) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_util_check),
                                            contentDescription = null,
                                            tint = MaterialTheme.cvColors.onAccent,
                                            modifier = Modifier.size(13.dp),
                                        )
                                        Spacer(Modifier.width(4.dp))
                                    }
                                    Text(
                                        cat.name.lowercase().replace('_', ' '),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (isSelected) MaterialTheme.cvColors.onAccent
                                        else MaterialTheme.cvColors.textSecondary,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        },
        // Explicit confirm: nothing happens until a chip is chosen and the
        // button is tapped.
        confirmButton = {
            TextButton(
                onClick = { chosen?.let(onPick) },
                enabled = chosen != null,
            ) {
                Text(
                    "Add to library",
                    color = if (chosen != null) NeonBlue else MaterialTheme.cvColors.textDisabled,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Cancel", color = NeonBlue)
            }
        },
    )
}

// ---- Formatting helpers (no fabricated metadata) ---------------------------------

private fun categoryLabel(cat: AnimationCategory): String =
    cat.name.lowercase().replace('_', ' ')

/** WxH when the entity knows both, otherwise an honest "Not reported". */
private fun formatResolution(item: MediaItemEntity): String =
    if (item.widthPx > 0 && item.heightPx > 0) "${item.widthPx} × ${item.heightPx}"
    else "Not reported"

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "Not reported"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb < 0.1) "%.2f MB".format(mb) else "%.1f MB".format(mb)
}

private fun formatDate(ms: Long): String =
    if (ms > 0) DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(ms)) else "Not reported"
