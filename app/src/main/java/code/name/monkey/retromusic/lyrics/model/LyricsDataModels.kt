package code.name.monkey.retromusic.lyrics.model

import kotlinx.coroutines.CompletableDeferred

data class CandidateRecord(
    val id: Long,
    val trackName: String,
    val artistName: String,
    val albumName: String?,
    val duration: Double?,
    val syncedLyrics: String,
    val score: Float
)

data class DisambiguationRequest(
    val filePath: String,
    val fileName: String,
    val localTitle: String,
    val localArtist: String,
    val localAlbum: String,
    val candidates: List<CandidateRecord>,
    val deferredChoice: CompletableDeferred<CandidateRecord?>
)

data class SyncDashboardStats(
    val totalFiles: Int = 0,
    val processedFiles: Int = 0,
    val alreadySyncedCount: Int = 0,
    val embeddedCount: Int = 0,
    val ambiguousPromptCount: Int = 0,
    val failedCount: Int = 0,
    val currentFileName: String = "",
    val currentStatusMessage: String = "Idle",
    val isRunning: Boolean = false,
    val logs: List<String> = emptyList()
)
