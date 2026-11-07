package com.example.ui.recordings

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audio.AudioPlayerManager
import com.example.data.model.RecordingItem
import com.example.data.repository.RecordingsRepository
import com.example.ui.theme.CallGreen
import com.example.ui.theme.CallRed

enum class RecordingSortOrder {
    NEWEST,
    OLDEST,
    DURATION_DESC,
    DURATION_ASC,
    NAME
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingsScreen(
    recordingsRepository: RecordingsRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val recordings by recordingsRepository.recordingsFlow.collectAsStateWithLifecycle()
    val playbackState by AudioPlayerManager.playbackState.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var sortOrder by remember { mutableStateOf(RecordingSortOrder.NEWEST) }
    var sortMenuExpanded by remember { mutableStateOf(false) }

    var itemToDelete by remember { mutableStateOf<RecordingItem?>(null) }
    var itemToRename by remember { mutableStateOf<RecordingItem?>(null) }
    var renameInput by remember { mutableStateOf("") }

    var storageStats by remember { mutableStateOf(recordingsRepository.getStorageStats()) }

    LaunchedEffect(Unit) {
        recordingsRepository.loadRecordings()
        storageStats = recordingsRepository.getStorageStats()
    }

    val filteredRecordings = remember(recordings, searchQuery, sortOrder) {
        var list = if (searchQuery.isBlank()) {
            recordings
        } else {
            recordings.filter { rec ->
                val nameMatch = rec.callerName?.contains(searchQuery, ignoreCase = true) == true
                val numMatch = rec.callerNumber?.contains(searchQuery) == true
                val fileMatch = rec.fileName.contains(searchQuery, ignoreCase = true)
                nameMatch || numMatch || fileMatch
            }
        }

        list = when (sortOrder) {
            RecordingSortOrder.NEWEST -> list.sortedByDescending { it.dateMillis }
            RecordingSortOrder.OLDEST -> list.sortedBy { it.dateMillis }
            RecordingSortOrder.DURATION_DESC -> list.sortedByDescending { it.durationSeconds }
            RecordingSortOrder.DURATION_ASC -> list.sortedBy { it.durationSeconds }
            RecordingSortOrder.NAME -> list.sortedBy { it.callerName ?: it.callerNumber ?: it.fileName }
        }
        list
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Storage Usage Header Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${recordings.size} Recordings • ${storageStats.formattedUsed} used",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "${storageStats.formattedAvailable} free",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Search & Sort Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .weight(1f)
                    .testTag("recordings_search_input"),
                placeholder = { Text("Search recordings…") },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = null)
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )

            Box(modifier = Modifier.padding(start = 6.dp)) {
                IconButton(onClick = { sortMenuExpanded = true }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Sort,
                        contentDescription = "Sort Recordings",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                DropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = { sortMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Newest first") },
                        onClick = {
                            sortOrder = RecordingSortOrder.NEWEST
                            sortMenuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Oldest first") },
                        onClick = {
                            sortOrder = RecordingSortOrder.OLDEST
                            sortMenuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Longest duration") },
                        onClick = {
                            sortOrder = RecordingSortOrder.DURATION_DESC
                            sortMenuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Shortest duration") },
                        onClick = {
                            sortOrder = RecordingSortOrder.DURATION_ASC
                            sortMenuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Name / Contact") },
                        onClick = {
                            sortOrder = RecordingSortOrder.NAME
                            sortMenuExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Recording List or Empty State
        if (filteredRecordings.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (searchQuery.isNotBlank()) "No recordings match your search" else "No call recordings yet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (searchQuery.isNotBlank()) "Try searching with a different term" else "Eligible recorded calls will appear here. Tap the Record button during calls to record audio.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredRecordings, key = { it.id }) { item ->
                    RecordingCardItem(
                        item = item,
                        isPlaying = playbackState.activeRecordingId == item.id && playbackState.isPlaying,
                        currentPositionMs = if (playbackState.activeRecordingId == item.id) playbackState.currentPositionMs else 0,
                        totalDurationMs = if (playbackState.activeRecordingId == item.id) playbackState.totalDurationMs else (item.durationSeconds * 1000).toInt(),
                        onPlayClick = {
                            if (playbackState.activeRecordingId == item.id && playbackState.isPlaying) {
                                AudioPlayerManager.pause()
                            } else {
                                AudioPlayerManager.play(item)
                            }
                        },
                        onSeek = { position ->
                            AudioPlayerManager.seekTo(position)
                        },
                        onRenameClick = {
                            itemToRename = item
                            renameInput = item.callerName ?: item.callerNumber ?: item.fileName.substringBeforeLast(".")
                        },
                        onDeleteClick = {
                            itemToDelete = item
                        },
                        onShareClick = {
                            val shareIntent = recordingsRepository.getShareIntent(item)
                            if (shareIntent != null) {
                                val chooser = Intent.createChooser(shareIntent, "Share Call Recording")
                                chooser.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                context.startActivity(chooser)
                            }
                        }
                    )
                }
            }
        }
    }

    // Delete Confirmation Dialog
    itemToDelete?.let { rec ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("Delete Recording") },
            text = { Text("Are you sure you want to permanently delete '${rec.fileName}'? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = rec.id
                        if (playbackState.activeRecordingId == id) {
                            AudioPlayerManager.stop()
                        }
                        coroutineScope.launch {
                            recordingsRepository.deleteRecording(id)
                        }
                        itemToDelete = null
                    }
                ) {
                    Text("Delete", color = CallRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Rename Dialog
    itemToRename?.let { rec ->
        AlertDialog(
            onDismissRequest = { itemToRename = null },
            title = { Text("Rename Recording") },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text("Recording Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (renameInput.isNotBlank()) {
                            coroutineScope.launch {
                                recordingsRepository.renameRecording(rec.id, renameInput)
                            }
                            itemToRename = null
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToRename = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun RecordingCardItem(
    item: RecordingItem,
    isPlaying: Boolean,
    currentPositionMs: Int,
    totalDurationMs: Int,
    onPlayClick: () -> Unit,
    onSeek: (Int) -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onShareClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("recording_item_${item.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header Row: Avatar, Name/Number, Timestamp, Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Play / Pause Circle
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (isPlaying) CallRed else MaterialTheme.colorScheme.primary)
                        .clickable(onClick = onPlayClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.callerName ?: item.callerNumber ?: item.fileName,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${item.formattedDate} • ${item.formattedDuration} • ${item.formattedSize}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row {
                    IconButton(onClick = onShareClick, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onRenameClick, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Default.DriveFileRenameOutline,
                            contentDescription = "Rename",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onDeleteClick, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = CallRed,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Audio Player Seekbar if active/playing or position > 0
            if (currentPositionMs > 0 || isPlaying) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(modifier = Modifier.fillMaxWidth()) {
                    val safeTotal = totalDurationMs.coerceAtLeast(1)
                    val progress = (currentPositionMs.toFloat() / safeTotal.toFloat()).coerceIn(0f, 1f)

                    Slider(
                        value = progress,
                        onValueChange = { newProgress ->
                            val targetMs = (newProgress * safeTotal).toInt()
                            onSeek(targetMs)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatMs(currentPositionMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatMs(totalDurationMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun formatMs(ms: Int): String {
    val totalSecs = ms / 1000
    val mins = totalSecs / 60
    val secs = totalSecs % 60
    return String.format(java.util.Locale.US, "%02d:%02d", mins, secs)
}
