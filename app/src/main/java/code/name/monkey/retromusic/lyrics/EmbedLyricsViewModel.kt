package code.name.monkey.retromusic.lyrics

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import code.name.monkey.retromusic.lyrics.model.CandidateRecord
import code.name.monkey.retromusic.lyrics.model.DisambiguationRequest
import code.name.monkey.retromusic.lyrics.model.SyncDashboardStats
import code.name.monkey.retromusic.lyrics.network.LrclibRepository
import code.name.monkey.retromusic.lyrics.tagger.AudioTaggerHelper
import code.name.monkey.retromusic.lyrics.transliteration.LyricsTransliterator
import code.name.monkey.retromusic.model.Song
import code.name.monkey.retromusic.repository.SongRepository
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

    private var workerJob: Job? = null

    fun startSync() {
        if (workerJob?.isActive == true) return

        workerJob = viewModelScope.launch(Dispatchers.IO) {
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

            val concurrencySemaphore = Semaphore(4)

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
            _stats.update {
                it.copy(
                    isRunning = false,
                    currentStatusMessage = "Sync Completed!"
                )
            }
            addLog("Sync completed successfully.")
        }
    }

    private suspend fun processSingleSong(song: Song) {
        val file = File(song.data)

        if (AudioTaggerHelper.hasSyncedLyrics(song.data)) {
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
            _stats.update {
                it.copy(ambiguousPromptCount = it.ambiguousPromptCount + 1)
            }
            addLog("[Disambiguation] ${song.title} - Awaiting decision")

            val deferred = CompletableDeferred<CandidateRecord?>()
            _activePrompt.value = DisambiguationRequest(
                filePath = song.data,
                fileName = file.name,
                localTitle = song.title,
                localArtist = song.artistName,
                localAlbum = song.albumName,
                candidates = candidates.take(3),
                deferredChoice = deferred
            )
            deferred.await()
        }

        if (chosenCandidate != null) {
            val transliterated = LyricsTransliterator.transliterateLrc(chosenCandidate.syncedLyrics)
            val success = AudioTaggerHelper.embedLyrics(context, file, transliterated)

            if (success) {
                _stats.update {
                    it.copy(
                        processedFiles = it.processedFiles + 1,
                        embeddedCount = it.embeddedCount + 1
                    )
                }
                addLog("[Embedded] ${song.title}")
            } else {
                _stats.update {
                    it.copy(
                        processedFiles = it.processedFiles + 1,
                        failedCount = it.failedCount + 1
                    )
                }
                addLog("[Error Embedding] ${song.title}")
            }
        } else {
            _stats.update {
                it.copy(
                    processedFiles = it.processedFiles + 1,
                    failedCount = it.failedCount + 1
                )
            }
            addLog("[Skipped] ${song.title}")
        }
    }

    fun resolveUserChoice(candidate: CandidateRecord?) {
        _activePrompt.value?.deferredChoice?.complete(candidate)
        _activePrompt.value = null
    }

    fun cancelSync() {
        workerJob?.cancel()
        _activePrompt.value?.deferredChoice?.cancel()
        _activePrompt.value = null
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
