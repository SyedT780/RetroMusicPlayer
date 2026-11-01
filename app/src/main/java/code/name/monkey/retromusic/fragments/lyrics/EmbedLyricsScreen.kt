package code.name.monkey.retromusic.fragments.lyrics

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.res.painterResource
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
import code.name.monkey.appthemehelper.util.ATHUtil
import code.name.monkey.retromusic.R
import code.name.monkey.retromusic.extensions.accentColor
import code.name.monkey.retromusic.helper.MusicPlayerRemote
import code.name.monkey.retromusic.lyrics.EmbedLyricsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmbedLyricsScreen(
    viewModel: EmbedLyricsViewModel,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val stats by viewModel.stats.collectAsState()
    val activePrompt by viewModel.activePrompt.collectAsState()
    val skipExisting by viewModel.skipExistingEmbedded.collectAsState()

    var isPlaying by remember { mutableStateOf(MusicPlayerRemote.isPlaying) }

    var hasPermission by remember {
        mutableStateOf(checkStoragePermission(context))
    }

    LaunchedEffect(Unit) {
        hasPermission = checkStoragePermission(context)
    }

    val accent = Color(context.accentColor())
    val isDark = ATHUtil.isWindowBackgroundDark(context)
    val bgColor = Color(ATHUtil.resolveColor(context, android.R.attr.colorBackground))
    val surfaceColor = Color(ATHUtil.resolveColor(context, com.google.android.material.R.attr.colorSurface))
    val primaryTextColor = Color(ATHUtil.resolveColor(context, android.R.attr.textColorPrimary))
    val secondaryTextColor = Color(ATHUtil.resolveColor(context, android.R.attr.textColorSecondary))

    val colorScheme = if (isDark) {
        darkColorScheme(
            primary = accent,
            onPrimary = Color.White,
            primaryContainer = accent.copy(alpha = 0.2f),
            onPrimaryContainer = accent,
            background = bgColor,
            onBackground = primaryTextColor,
            surface = surfaceColor,
            onSurface = primaryTextColor,
            surfaceVariant = surfaceColor,
            onSurfaceVariant = secondaryTextColor
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = Color.White,
            primaryContainer = accent.copy(alpha = 0.15f),
            onPrimaryContainer = accent,
            background = bgColor,
            onBackground = primaryTextColor,
            surface = surfaceColor,
            onSurface = primaryTextColor,
            surfaceVariant = surfaceColor,
            onSurfaceVariant = secondaryTextColor
        )
    }

    MaterialTheme(colorScheme = colorScheme) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    ),
                    title = {
                        Text(
                            text = stringResource(R.string.embed_lyrics_title),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    },
                    actions = {
                        if (stats.isRunning) {
                            Button(
                                onClick = { viewModel.cancelSync() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                shape = RoundedCornerShape(20.dp)
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
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = accent),
                                shape = RoundedCornerShape(20.dp)
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
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!hasPermission) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
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

                // Skip Existing Toggle Option
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Skip tracks with existing embedded lyrics",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Switch(
                        checked = skipExisting,
                        onCheckedChange = { viewModel.toggleSkipExistingEmbedded(it) }
                    )
                }

                // Metric Cards Grid
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
                            modifier = Modifier
                                .weight(1f)
                                .clickable { viewModel.reOpenPendingDecisions() }
                        )
                        MetricCard(
                            title = stringResource(R.string.not_found_errors),
                            value = stats.failedCount.toString(),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Progress Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val progress = if (stats.totalFiles > 0) stats.processedFiles.toFloat() / stats.totalFiles else 0f
                        LinearProgressIndicator(
                            progress = { progress },
                            color = accent,
                            trackColor = accent.copy(alpha = 0.2f),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stats.currentStatusMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${stats.processedFiles}/${stats.totalFiles}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (stats.currentFileName.isNotBlank()) {
                            Text(
                                text = "Current: ${stats.currentFileName}",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    thickness = 1.dp
                )

                // Activity Log Header & Feed
                Text(
                    text = "Activity Log",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                val logBg = if (isDark) Color(0xFF181818) else Color(0xFFF2F2F2)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = logBg
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
                                log.contains("[Awaiting Decision]") || log.contains("[Disambiguation]") -> Color(0xFFFF9800)
                                log.contains("[Not Found]") || log.contains("[Error") || log.contains("[Skipped]") -> Color(0xFFF44336)
                                else -> MaterialTheme.colorScheme.onSurface
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

        // Interactive Disambiguation Modal Dialog
        activePrompt?.let { prompt ->
            AlertDialog(
                onDismissRequest = { viewModel.resolveUserChoice(null) },
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Awaiting Decision (${stats.ambiguousPromptCount} remaining)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                if (MusicPlayerRemote.isPlaying) {
                                    MusicPlayerRemote.pauseSong()
                                } else {
                                    MusicPlayerRemote.resumePlaying()
                                }
                                isPlaying = MusicPlayerRemote.isPlaying
                            }
                        ) {
                            Icon(
                                painter = painterResource(id = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow),
                                contentDescription = "Play/Pause",
                                tint = accent
                            )
                        }
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Track: ${prompt.localTitle}",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Artist: ${prompt.localArtist} | Album: ${prompt.localAlbum}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        prompt.candidates.forEachIndexed { index, candidate ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.resolveUserChoice(candidate) },
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = accent.copy(alpha = 0.08f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
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
                                            color = accent,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(
                                        text = "Artist: ${candidate.artistName} | Album: ${candidate.albumName ?: "N/A"}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    val previewSnippet = candidate.syncedLyrics.lines()
                                        .filter { it.contains("]") && it.length > 10 }
                                        .take(2)
                                        .joinToString("\n")
                                    if (previewSnippet.isNotBlank()) {
                                        Text(
                                            text = previewSnippet,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier
                                                .background(
                                                    MaterialTheme.colorScheme.surface,
                                                    RoundedCornerShape(4.dp)
                                                )
                                                .padding(4.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Button(
                                        onClick = { viewModel.resolveUserChoice(candidate) },
                                        colors = ButtonDefaults.buttonColors(containerColor = accent),
                                        modifier = Modifier.align(Alignment.End),
                                        shape = RoundedCornerShape(16.dp)
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
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
