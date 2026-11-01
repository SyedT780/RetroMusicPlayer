package code.name.monkey.retromusic.extensions

import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.session.MediaSessionCompat.QueueItem
import code.name.monkey.retromusic.model.Song
import code.name.monkey.retromusic.util.MurmurHash3
import code.name.monkey.retromusic.util.MusicUtil

val Song.uri get() = MusicUtil.getSongFileUri(songId = id)

val Song.albumArtUri get() = MusicUtil.getMediaStoreAlbumCoverUri(albumId)

val Song.stringId get() = id.toString()

fun Song.computeInvariantHash(): Long {
    val canonicalString = "${title.trim()}|${artistName.trim()}|${albumName.trim()}"
    return MurmurHash3.hash64(canonicalString)
}

fun List<Song>.computeInvariantRanks(): Map<Long, Int> {
    if (isEmpty()) return emptyMap()
    val distinctSongs = distinctBy { it.id }
    val sortedByHash = distinctSongs.sortedWith(
        compareBy({ it.computeInvariantHash() }, { it.id })
    )
    val rankMap = HashMap<Long, Int>(sortedByHash.size)
    for ((rank, song) in sortedByHash.withIndex()) {
        rankMap[song.id] = rank
    }
    return rankMap
}

fun ArrayList<Song>.toMediaSessionQueue(): List<QueueItem> {
    return map { song ->
        val mediaDescription = MediaDescriptionCompat.Builder()
            .setMediaId(song.id.toString())
            .setTitle(song.title)
            .setSubtitle(song.artistName)
            .setIconUri(song.albumArtUri)
            .build()
        QueueItem(mediaDescription, song.hashCode().toLong())
    }
}
