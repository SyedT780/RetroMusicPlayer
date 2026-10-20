package code.name.monkey.retromusic.lyrics.tagger

import android.content.Context
import android.media.MediaScannerConnection
import code.name.monkey.retromusic.model.lyrics.Lyrics
import code.name.monkey.retromusic.util.LyricUtil
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File

object AudioTaggerHelper {

    data class Inspection(
        val hasSynced: Boolean,
        val title: String,
        val artist: String,
        val album: String,
        val durationSec: Double
    )

    fun hasSyncedLyrics(filePath: String): Boolean {
        val embedded = LyricUtil.getEmbeddedSyncedLyrics(filePath)
        if (!embedded.isNullOrBlank()) {
            return true
        }
        return try {
            val file = File(filePath)
            if (!file.exists()) return false
            val audioFile = AudioFileIO.read(file)
            val lyricsText = audioFile.tagOrCreateDefault.getFirst(FieldKey.LYRICS)
            !lyricsText.isNullOrBlank() && Lyrics.isSynchronized(lyricsText)
        } catch (e: Exception) {
            false
        }
    }

    fun embedLyrics(context: Context?, file: File, lrcContent: String): Boolean {
        return try {
            if (!file.exists()) return false
            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tagOrCreateAndSetDefault
            tag.setField(FieldKey.LYRICS, lrcContent)
            audioFile.commit()

            if (context != null) {
                MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
