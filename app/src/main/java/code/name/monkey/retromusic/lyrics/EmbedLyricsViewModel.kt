package code.name.monkey.retromusic.lyrics

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import code.name.monkey.retromusic.lyrics.model.CandidateRecord
import code.name.monkey.retromusic.lyrics.model.DisambiguationRequest
import code.name.monkey.retromusic.lyrics.model.SyncDashboardStats
import code.name.monkey.retromusic.lyrics.network.LrclibRepository
import code.name.monkey.retromusic.lyrics.tagger.AudioTaggerHelper
import code.name.monkey.retromusic.model.Song
import code.name.monkey.retromusic.repository.SongRepository
import code.name.monkey.retromusic.util.LyricUtil
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import java.io.File

class EmbedLyricsViewModel(
    private val songRepository: SongRepository,
    private val context: Context
) : ViewModel() {

    private val repository = LrclibRepository()

    private val _stats = MutableStateFlow(SyncDashboardStats())
    val stats: StateFlow<SyncDashboardStats> = _stats.asStateFlow()

    private val _activePrompt = MutableStateFlow<DisambiguationRequest?>(null)
    val activePrompt: StateFlow<DisambiguationRequest?> = _activePrompt.asStateFlow()

    private val _deferredDisambiguations = MutableStateFlow<List<DisambiguationRequest>>(emptyList())
    val deferredDisambiguations: StateFlow<List<DisambiguationRequest>> = _deferredDisambiguations.asStateFlow()

    val skipExistingEmbedded = MutableStateFlow(true)

    private var workerJob: Job? = null

    fun toggleSkipExistingEmbedded(skip: Boolean) {
        skipExistingEmbedded.value = skip
    }

    fun reOpenPendingDecisions() {
        if (_activePrompt.value == null && _deferredDisambiguations.value.isNotEmpty()) {
            val remaining = _deferredDisambiguations.value
            val firstRequest = remaining.first()
            _deferredDisambiguations.update { it.drop(1) }
            _activePrompt.value = firstRequest
        }
    }

    fun startSync() {
        if (workerJob?.isActive == true) return

        workerJob = viewModelScope.launch(Dispatchers.IO) {
            _deferredDisambiguations.value = emptyList()
            _activePrompt.value = null
            _stats.value = SyncDashboardStats(
                isRunning = true,
                currentStatusMessage = "Scanning music library..."
            )
            addLog("Starting music library scan...")

            val allSongs = songRepository.songs()
            val validSongs = allSongs.filter { song ->
                if (song.data.isBlank()) false
                else {
                    val file = File(song.data)
                    file.exists() && isSupportedAudioExtension(file.extension)
                }
            }

            _stats.update {
                it.copy(
                    totalFiles = validSongs.size,
                    currentStatusMessage = "Processing ${validSongs.size} audio files..."
                )
            }
            addLog("Found ${validSongs.size} audio files in library.")

            val concurrencySemaphore = Semaphore(8)

            val tasks = validSongs.map { song ->
                launch {
                    concurrencySemaphore.acquire()
                    try {
                        processSingleSong(song)
                    } finally {
                        concurrencySemaphore.release()
                    }
                }
            }

            tasks.joinAll()

            val queuedRequests = _deferredDisambiguations.value
            if (queuedRequests.isNotEmpty()) {
                _stats.update {
                    it.copy(
                        currentStatusMessage = "Scan complete. ${queuedRequests.size} tracks await decision."
                    )
                }
                addLog("Scan complete. Starting decision phase for ${queuedRequests.size} tracks...")
                val firstRequest = queuedRequests.first()
                _deferredDisambiguations.update { it.drop(1) }
                _activePrompt.value = firstRequest
            } else {
                _stats.update {
                    it.copy(
                        isRunning = false,
                        currentStatusMessage = "Sync Completed!"
                    )
                }
                addLog("Sync completed successfully.")
            }
        }
    }

    private suspend fun processSingleSong(song: Song) {
        val file = File(song.data)

        if (skipExistingEmbedded.value && (AudioTaggerHelper.hasSyncedLyrics(song.data) || LyricUtil.getEmbeddedSyncedLyrics(song.data) != null || LyricUtil.getSyncedLyricsFile(song) != null)) {
            _stats.update {
                it.copy(
                    processedFiles = it.processedFiles + 1,
                    alreadySyncedCount = it.alreadySyncedCount + 1
                )
            }
            addLog("[Already Synced] ${song.title}")
            return
        }

        _stats.update {
            it.copy(
                currentFileName = song.title,
                currentStatusMessage = "Searching LRCLIB for ${song.title}..."
            )
        }

        val candidates = repository.findCandidates(
            song.title,
            song.artistName,
            song.albumName,
            song.duration / 1000.0
        )

        if (candidates.isEmpty()) {
            _stats.update {
                it.copy(
                    processedFiles = it.processedFiles + 1,
                    failedCount = it.failedCount + 1
                )
            }
            addLog("[Not Found] ${song.title}")
            return
        }

        val chosenCandidate: CandidateRecord? = if (candidates.size == 1 && candidates[0].score >= 0.85f) {
            candidates[0]
        } else if (candidates.size >= 2 && candidates[0].score >= 0.85f && (candidates[0].score - candidates[1].score) >= 0.12f) {
            candidates[0]
        } else {
            null
        }

        if (chosenCandidate != null) {
            embedLyricsForCandidate(song, file, chosenCandidate, isFromDeferred = false)
        } else {
            _stats.update {
                it.copy(
                    processedFiles = it.processedFiles + 1,
                    ambiguousPromptCount = it.ambiguousPromptCount + 1
                )
            }
            addLog("[Awaiting Decision] ${song.title} - Queued for review")

            val deferred = CompletableDeferred<CandidateRecord?>()
            val request = DisambiguationRequest(
                filePath = song.data,
                fileName = file.name,
                localTitle = song.title,
                localArtist = song.artistName,
                localAlbum = song.albumName,
                candidates = candidates.take(3),
                deferredChoice = deferred
            )
            _deferredDisambiguations.update { current ->
                current + request
            }
        }
    }

    private suspend fun embedLyricsForCandidate(
        song: Song,
        file: File,
        candidate: CandidateRecord,
        isFromDeferred: Boolean
    ) {
        val success = AudioTaggerHelper.embedLyrics(context, file, candidate.syncedLyrics)

        if (success) {
            _stats.update {
                it.copy(
                    processedFiles = if (isFromDeferred) it.processedFiles else it.processedFiles + 1,
                    embeddedCount = it.embeddedCount + 1,
                    ambiguousPromptCount = if (isFromDeferred) (it.ambiguousPromptCount - 1).coerceAtLeast(0) else it.ambiguousPromptCount
                )
            }
            addLog("[Embedded] ${song.title}")
        } else {
            _stats.update {
                it.copy(
                    processedFiles = if (isFromDeferred) it.processedFiles else it.processedFiles + 1,
                    failedCount = it.failedCount + 1,
                    ambiguousPromptCount = if (isFromDeferred) (it.ambiguousPromptCount - 1).coerceAtLeast(0) else it.ambiguousPromptCount
                )
            }
            addLog("[Error Embedding] ${song.title}")
        }
    }

    fun resolveUserChoice(candidate: CandidateRecord?) {
        val currentPrompt = _activePrompt.value ?: return
        currentPrompt.deferredChoice.complete(candidate)

        viewModelScope.launch(Dispatchers.IO) {
            if (candidate != null) {
                val file = File(currentPrompt.filePath)
                if (file.exists()) {
                    val song = Song(
                        id = -1,
                        title = currentPrompt.localTitle,
                        trackNumber = -1,
                        year = -1,
                        duration = 0,
                        data = currentPrompt.filePath,
                        dateModified = 0,
                        albumId = -1,
                        albumName = currentPrompt.localAlbum,
                        artistId = -1,
                        artistName = currentPrompt.localArtist,
                        composer = null,
                        albumArtist = null
                    )
                    embedLyricsForCandidate(song, file, candidate, isFromDeferred = true)
                }
            } else {
                _stats.update {
                    it.copy(
                        failedCount = it.failedCount + 1,
                        ambiguousPromptCount = (it.ambiguousPromptCount - 1).coerceAtLeast(0)
                    )
                }
                addLog("[Skipped] ${currentPrompt.localTitle}")
            }

            processNextDeferred()
        }
    }

    private fun processNextDeferred() {
        val remaining = _deferredDisambiguations.value
        if (remaining.isNotEmpty()) {
            val nextRequest = remaining.first()
            _deferredDisambiguations.update { it.drop(1) }
            _activePrompt.value = nextRequest
        } else {
            _activePrompt.value = null
            _stats.update {
                it.copy(
                    isRunning = false,
                    currentStatusMessage = "All done!"
                )
            }
            addLog("All sync decisions completed.")
        }
    }

    fun cancelSync() {
        workerJob?.cancel()
        _activePrompt.value?.deferredChoice?.cancel()
        _activePrompt.value = null
        _deferredDisambiguations.value.forEach { request ->
            request.deferredChoice.cancel()
        }
        _deferredDisambiguations.value = emptyList()
        _stats.update {
            it.copy(
                isRunning = false,
                currentStatusMessage = "Cancelled by user."
            )
        }
        addLog("Sync cancelled by user.")
    }

    private fun addLog(message: String) {
        _stats.update { current ->
            val updatedLogs = (current.logs + message).takeLast(100)
            current.copy(logs = updatedLogs)
        }
    }

    private fun isSupportedAudioExtension(ext: String): Boolean {
        val lower = ext.lowercase()
        return lower in listOf("mp3", "flac", "m4a", "aac", "ogg", "wav", "opus")
    }
}
