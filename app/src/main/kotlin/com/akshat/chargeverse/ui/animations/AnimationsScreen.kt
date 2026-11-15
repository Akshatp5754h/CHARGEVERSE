package com.akshat.chargeverse.ui.animations

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.akshat.chargeverse.R
import com.akshat.chargeverse.core.theme.DimText
import com.akshat.chargeverse.core.theme.GlassFill
import com.akshat.chargeverse.core.theme.NeonBlue
import com.akshat.chargeverse.data.db.AnimationCategory
import com.akshat.chargeverse.data.db.MediaType
import com.akshat.chargeverse.ui.components.CvIcon
import com.akshat.chargeverse.ui.components.ConfirmDeleteDialog
import com.akshat.chargeverse.ui.components.EmptyState

@Composable
fun AnimationsScreen(viewModel: AnimationsViewModel = viewModel()) {
    val state by viewModel.ui.collectAsState()
    var deleteCandidate by remember { mutableStateOf<com.akshat.chargeverse.data.db.MediaItemEntity?>(null) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.stageImport(it) }
    }

    Column(Modifier.fillMaxSize().padding(top = 56.dp)) {
        Row(
            Modifier.padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Animations", style = MaterialTheme.typography.headlineMedium)
                Text("${state.items.size} in library", style = MaterialTheme.typography.bodyMedium, color = DimText)
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { picker.launch(arrayOf("video/*", "image/*")) }
                    .background(NeonBlue.copy(alpha = 0.16f), MaterialTheme.shapes.small)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CvIcon(R.drawable.ic_util_import, NeonBlue, Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Import", style = MaterialTheme.typography.labelLarge, color = NeonBlue)
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        // Category filter chips (original CHARGEVERSE categories)
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CategoryChip("All", state.category == null) { viewModel.selectCategory(null) }
            AnimationCategory.entries.forEach { cat ->
                CategoryChip(
                    cat.name.lowercase().replace('_', ' '),
                    state.category == cat,
                ) { viewModel.selectCategory(cat) }
            }
        }
        Spacer(Modifier.height(12.dp))

        state.error?.let { err ->
            Text(
                err,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }

        // Category chooser for a freshly picked file
        val pending = state.pendingUri
        if (pending != null) {
            CategoryChooserDialog(
                onPick = { viewModel.import(pending, it) },
                onCancel = { viewModel.cancelImport() },
            )
        }

        if (state.items.isEmpty()) {
            EmptyState(
                iconRes = R.drawable.ic_util_sparkle,
                title = "No animations yet",
                body = "Import videos, GIFs, WebP or images to build your charging universe.",
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 20.dp, end = 20.dp, top = 4.dp, bottom = 140.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.items, key = { it.id }) { item ->
                    MediaTile(item) { deleteCandidate = item }
                }
            }
        }
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

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .background(
                if (selected) NeonBlue.copy(alpha = 0.2f) else GlassFill,
                RoundedCornerShape(999.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) NeonBlue else DimText,
        )
    }
}

@Composable
private fun CategoryChooserDialog(
    onPick: (AnimationCategory) -> Unit,
    onCancel: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Choose a category") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    "Where should this animation live in your library?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = DimText,
                )
                AnimationCategory.entries.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { cat ->
                            Box(
                                Modifier
                                    .weight(1f)
                                    .background(GlassFill, MaterialTheme.shapes.small)
                                    .clickable { onPick(cat) }
                                    .padding(10.dp)
                            ) {
                                Text(
                                    cat.name.lowercase().replace('_', ' '),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = NeonBlue,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onCancel) { Text("Cancel") }
        },
    )
}



@Composable
private fun MediaTile(item: com.akshat.chargeverse.data.db.MediaItemEntity, onDelete: () -> Unit) {
    Column(
        Modifier
            .clip(MaterialTheme.shapes.medium)
            .background(GlassFill)
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.9f).background(Color(0xFF0A0A1A))) {
            AsyncImage(
                model = item.uri,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(Color(0x99000000), RoundedCornerShape(8.dp))
                    .padding(4.dp),
            ) {
                Text(
                    when (item.type) {
                        MediaType.VIDEO -> "video"
                        MediaType.GIF -> "gif"
                        MediaType.WEBP -> "webp"
                        MediaType.LOTTIE -> "lottie"
                        MediaType.IMAGE -> "image"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                )
            }
        }
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                item.title,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            CvIcon(R.drawable.ic_util_trash, DimText, Modifier.size(15.dp).clickable(onClick = onDelete))
        }
    }
}
