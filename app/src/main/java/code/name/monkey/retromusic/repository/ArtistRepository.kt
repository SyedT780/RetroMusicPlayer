/*
 * Copyright (c) 2019 Hemanth Savarala.
 *
 * Licensed under the GNU General Public License v3
 *
 * This is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by
 *  the Free Software Foundation either version 3 of the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 */

package code.name.monkey.retromusic.repository

import android.provider.MediaStore.Audio.AudioColumns
import code.name.monkey.retromusic.ALBUM_ARTIST
import code.name.monkey.retromusic.helper.SortOrder
import code.name.monkey.retromusic.model.Album
import code.name.monkey.retromusic.model.Artist
import code.name.monkey.retromusic.model.Song
import code.name.monkey.retromusic.util.ArtistSeparator
import code.name.monkey.retromusic.util.PreferenceUtil
import java.text.Collator

interface ArtistRepository {
    fun artists(): List<Artist>

    fun artists(albums: List<Album>, albumArtistsOnly: Boolean): List<Artist>

    fun albumArtists(): List<Artist>

    fun albumArtists(query: String): List<Artist>

    fun artists(query: String): List<Artist>

    fun artist(artistId: Long): Artist

    fun albumArtist(artistName: String): Artist
}

class RealArtistRepository(
    private val songRepository: RealSongRepository,
    private val albumRepository: RealAlbumRepository
) : ArtistRepository {

    private fun getSongLoaderSortOrder(): String {
        return PreferenceUtil.artistSortOrder + ", " +
                PreferenceUtil.artistAlbumSortOrder + ", " +
                PreferenceUtil.artistSongSortOrder
    }

    override fun artist(artistId: Long): Artist {
        if (artistId == Artist.VARIOUS_ARTISTS_ID) {
            // Get Various Artists
            val songs = songRepository.songs(
                songRepository.makeSongCursor(
                    null,
                    null,
                    getSongLoaderSortOrder()
                )
            )
            val albums = albumRepository.splitIntoAlbums(songs)
                .filter { it.albumArtist == Artist.VARIOUS_ARTISTS_DISPLAY_NAME }
            return Artist(Artist.VARIOUS_ARTISTS_ID, albums)
        }

        val allArtists = artists()
        val found = allArtists.find { it.id == artistId }
        if (found != null) {
            return found
        }

        val songs = songRepository.songs(
            songRepository.makeSongCursor(
                AudioColumns.ARTIST_ID + "=?",
                arrayOf(artistId.toString()),
                getSongLoaderSortOrder()
            )
        )
        return Artist(artistId, albumRepository.splitIntoAlbums(songs))
    }

    override fun albumArtist(artistName: String): Artist {
        if (artistName == Artist.VARIOUS_ARTISTS_DISPLAY_NAME) {
            // Get Various Artists
            val songs = songRepository.songs(
                songRepository.makeSongCursor(
                    null,
                    null,
                    getSongLoaderSortOrder()
                )
            )
            val albums = albumRepository.splitIntoAlbums(songs)
                .filter { it.albumArtist == Artist.VARIOUS_ARTISTS_DISPLAY_NAME }
            return Artist(Artist.VARIOUS_ARTISTS_ID, albums, true)
        }

        val allAlbumArtists = albumArtists()
        val found = allAlbumArtists.find { it.name.equals(artistName, ignoreCase = true) }
        if (found != null) {
            return found
        }

        val songs = songRepository.songs(
            songRepository.makeSongCursor(
                "album_artist" + "=?",
                arrayOf(artistName),
                getSongLoaderSortOrder()
            )
        )
        return Artist(artistName, albumRepository.splitIntoAlbums(songs), true)
    }

    override fun artists(): List<Artist> {
        val songs = songRepository.songs(
            songRepository.makeSongCursor(
                null, null,
                getSongLoaderSortOrder()
            )
        )
        val artists = splitIntoArtists(albumRepository.splitIntoAlbums(songs))
        return sortArtists(artists)
    }

    override fun artists(albums: List<Album>, albumArtistsOnly: Boolean): List<Artist> {
        val artists = if (albumArtistsOnly) {
            splitIntoAlbumArtists(albums)
        } else {
            splitIntoArtists(albums)
        }
        return sortArtists(artists)
    }

    override fun albumArtists(): List<Artist> {
        val songs = songRepository.songs(
            songRepository.makeSongCursor(
                null,
                null,
                "lower($ALBUM_ARTIST)" +
                        if (PreferenceUtil.artistSortOrder == SortOrder.ArtistSortOrder.ARTIST_A_Z) "" else " DESC"
            )
        )
        val artists = splitIntoAlbumArtists(albumRepository.splitIntoAlbums(songs))
        return sortArtists(artists)
    }

    override fun albumArtists(query: String): List<Artist> {
        return albumArtists().filter { it.name.contains(query, ignoreCase = true) }
    }

    override fun artists(query: String): List<Artist> {
        return artists().filter { it.name.contains(query, ignoreCase = true) }
    }

    private fun splitIntoAlbumArtists(albums: List<Album>): List<Artist> {
        val allSongs = albums.flatMap { it.songs }.distinctBy { it.id }

        val albumArtistNameSongPairs = mutableListOf<Pair<String, Song>>()
        for (song in allSongs) {
            val albumArtist = song.albumArtist
            val names = ArtistSeparator.split(albumArtist)
            if (names.isEmpty()) {
                if (!albumArtist.isNullOrEmpty()) {
                    albumArtistNameSongPairs.add(Pair(albumArtist, song))
                }
            } else {
                for (name in names) {
                    albumArtistNameSongPairs.add(Pair(name, song))
                }
            }
        }

        val groupedByArtistName = albumArtistNameSongPairs.groupBy { (name, _) -> name.lowercase() }

        return groupedByArtistName.map { (_, pairs) ->
            val displayName = pairs.first().first
            val artistSongs = pairs.map { it.second }.distinctBy { it.id }
            val artistAlbums = albumRepository.splitIntoAlbums(artistSongs, sorted = false)

            if (displayName.equals(Artist.VARIOUS_ARTISTS_DISPLAY_NAME, ignoreCase = true)) {
                Artist(Artist.VARIOUS_ARTISTS_ID, artistAlbums, true)
            } else {
                val primaryArtistId = (displayName.lowercase().hashCode().toLong() and 0x7FFFFFFF_FFFFFFFFL)

                Artist(
                    artistName = displayName,
                    albums = artistAlbums,
                    isAlbumArtist = true,
                    id = primaryArtistId
                )
            }
        }
    }

    fun splitIntoArtists(albums: List<Album>): List<Artist> {
        val allSongs = albums.flatMap { it.songs }.distinctBy { it.id }

        val artistNameSongPairs = mutableListOf<Pair<String, Song>>()
        for (song in allSongs) {
            val names = ArtistSeparator.split(song.artistName)
            if (names.isEmpty()) {
                val name = if (song.artistName.isBlank()) Artist.UNKNOWN_ARTIST_DISPLAY_NAME else song.artistName
                artistNameSongPairs.add(Pair(name, song))
            } else {
                for (name in names) {
                    artistNameSongPairs.add(Pair(name, song))
                }
            }
        }

        val groupedByArtistName = artistNameSongPairs.groupBy { (name, _) -> name.lowercase() }

        return groupedByArtistName.map { (_, pairs) ->
            val displayName = pairs.first().first
            val artistSongs = pairs.map { it.second }.distinctBy { it.id }
            val artistAlbums = albumRepository.splitIntoAlbums(artistSongs, sorted = false)

            val primaryArtistId = (displayName.lowercase().hashCode().toLong() and 0x7FFFFFFF_FFFFFFFFL)

            Artist(
                artistName = displayName,
                albums = artistAlbums,
                isAlbumArtist = false,
                id = primaryArtistId
            )
        }
    }

    private fun sortArtists(artists: List<Artist>): List<Artist> {
        val collator = Collator.getInstance()
        return when (PreferenceUtil.artistSortOrder) {
            SortOrder.ArtistSortOrder.ARTIST_A_Z -> {
                artists.sortedWith { a1, a2 -> collator.compare(a1.name, a2.name) }
            }
            SortOrder.ArtistSortOrder.ARTIST_Z_A -> {
                artists.sortedWith { a1, a2 -> collator.compare(a2.name, a1.name) }
            }
            else -> artists
        }
    }
}
