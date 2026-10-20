package code.name.monkey.retromusic.fragments.lyrics

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import code.name.monkey.retromusic.R
import code.name.monkey.retromusic.lyrics.EmbedLyricsViewModel
import code.name.monkey.retromusic.lyrics.model.CandidateRecord
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmbedLyricsScreen(
    viewModel: EmbedLyricsViewModel,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val stats by viewModel.stats.collectAsState()
    val activePrompt by viewModel.activePrompt.collectAsState()

    var hasPermission by remember {
        mutableStateOf(checkStoragePermission(context))
    }

    LaunchedEffect(Unit) {
        hasPermission = checkStoragePermission(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.embed_lyrics_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (stats.isRunning) {
                        Button(
                            onClick = { viewModel.cancelSync() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text(stringResource(R.string.cancel_sync))
                        }
                    } else {
                        Button(
                            onClick = {
                                if (hasPermission) {
                                    viewModel.startSync()
                                } else {
                                    requestStoragePermission(context)
                                }
                            }
                        ) {
                            Text(stringResource(R.string.start_sync))
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!hasPermission) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.storage_permission_required),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = stringResource(R.string.storage_permission_summary),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Button(
                            onClick = {
                                requestStoragePermission(context)
                                hasPermission = checkStoragePermission(context)
                            }
                        ) {
                            Text(stringResource(R.string.grant_permission))
                        }
                    }
                }
            }

            // Live Metric Cards Grid
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricCard(
                        title = stringResource(R.string.total_scanned),
                        value = stats.totalFiles.toString(),
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = stringResource(R.string.already_synced),
                        value = stats.alreadySyncedCount.toString(),
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = stringResource(R.string.synced_embedded),
                        value = stats.embeddedCount.toString(),
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricCard(
                        title = stringResource(R.string.awaiting_decision),
                        value = stats.ambiguousPromptCount.toString(),
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = stringResource(R.string.not_found_errors),
                        value = stats.failedCount.toString(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Progress & Active Status
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val progress = if (stats.totalFiles > 0) stats.processedFiles.toFloat() / stats.totalFiles else 0f
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stats.currentStatusMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${stats.processedFiles}/${stats.totalFiles}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (stats.currentFileName.isNotBlank()) {
                        Text(
                            text = "Current: ${stats.currentFileName}",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Activity Log Feed
            Text(
                text = "Activity Log",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF1E1E1E)
            ) {
                val listState = rememberLazyListState()
                LaunchedEffect(stats.logs.size) {
                    if (stats.logs.isNotEmpty()) {
                        listState.animateScrollToItem(stats.logs.size - 1)
                    }
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(stats.logs) { log ->
                        val logColor = when {
                            log.contains("[Embedded]") -> Color(0xFF4CAF50)
                            log.contains("[Already Synced]") -> Color(0xFF2196F3)
                            log.contains("[Disambiguation]") -> Color(0xFFFF9800)
                            log.contains("[Not Found]") || log.contains("[Error") -> Color(0xFFF44336)
                            else -> Color(0xFFE0E0E0)
                        }
                        Text(
                            text = log,
                            color = logColor,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }

    // Interactive Disambiguation Modal
    activePrompt?.let { prompt ->
        AlertDialog(
            onDismissRequest = { viewModel.resolveUserChoice(null) },
            title = {
                Text(
                    text = stringResource(R.string.disambiguation_title),
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Track: ${prompt.localTitle}",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Artist: ${prompt.localArtist} | Album: ${prompt.localAlbum}",
                        style = MaterialTheme.typography.bodySmall
                    )

                    prompt.candidates.forEachIndexed { index, candidate ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Option ${index + 1}: ${candidate.trackName}",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "${(candidate.score * 100).toInt()}% Match",
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = "Artist: ${candidate.artistName} | Album: ${candidate.albumName ?: "N/A"}",
                                    fontSize = 12.sp
                                )
                                candidate.duration?.let { dur ->
                                    val delta = dur - (prompt.candidates.firstOrNull()?.duration ?: dur)
                                    Text(
                                        text = "Duration Delta: ${String.format("%.1fs", delta)}",
                                        fontSize = 11.sp,
                                        color = Color.Gray
                                    )
                                }
                                val previewSnippet = candidate.syncedLyrics.lines()
                                    .filter { it.contains("]") && it.length > 10 }
                                    .take(2)
                                    .joinToString("\n")
                                if (previewSnippet.isNotBlank()) {
                                    Text(
                                        text = previewSnippet,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .background(
                                                Color.Black.copy(alpha = 0.05f),
                                                RoundedCornerShape(4.dp)
                                            )
                                            .padding(4.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Button(
                                    onClick = { viewModel.resolveUserChoice(candidate) },
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text("Select Option ${index + 1}")
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = { viewModel.resolveUserChoice(null) }
                ) {
                    Text(stringResource(R.string.skip_track))
                }
            }
        )
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun checkStoragePermission(context: android.content.Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        true
    }
}

private fun requestStoragePermission(context: android.content.Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            context.startActivity(intent)
        }
    }
}
