/*
 * Copyright (c) 2024 Retro Music Player.
 *
 * Licensed under the GNU General Public License v3
 *
 * This is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 */

package code.name.monkey.retromusic.helper

import code.name.monkey.retromusic.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnhancedShuffleEngineTest {

    private fun createDummySong(id: Long, artistId: Long, albumId: Long): Song {
        return Song(
            id = id,
            title = "Song $id",
            trackNumber = 1,
            year = 2024,
            duration = 180000,
            data = "/path/to/song_$id.mp3",
            dateModified = System.currentTimeMillis(),
            albumId = albumId,
            albumName = "Album $albumId",
            artistId = artistId,
            artistName = "Artist $artistId",
            composer = "",
            albumArtist = "",
        )
    }

    @Test
    fun testPRPBijection() {
        val testSizes = listOf(1, 2, 5, 10, 50, 100, 500, 1000)
        val seed = 123456789L

        for (n in testSizes) {
            val generatedIndices = HashSet<Int>()
            for (i in 0 until n) {
                val j = EnhancedShuffleEngine.generatePRPIndex(i, n, seed)
                assertTrue("PRP output $j out of bounds for N=$n", (j in 0 until n))
                generatedIndices.add(j)
            }
            assertEquals("PRP failed bijection for N=$n: missing indices", n, generatedIndices.size)
        }
    }

    @Test
    fun testNoDuplicateSongs() {
        val songs = (1L..50L).map { createDummySong(id = it, artistId = it % 5, albumId = it % 10) }.toMutableList()
        val originalIds = songs.map { it.id }.toSet()

        EnhancedShuffleEngine.shuffle(songs, current = 10, customSeed = 9999L)

        assertEquals(50, songs.size)
        val resultIds = songs.map { it.id }.toSet()
        assertEquals(originalIds, resultIds)
    }

    @Test
    fun testCurrentSongPreserved() {
        val songs = (1L..20L).map { createDummySong(id = it, artistId = it % 3, albumId = it % 5) }.toMutableList()
        val targetSong = songs[7]

        EnhancedShuffleEngine.shuffle(songs, current = 7, customSeed = 42L)

        assertEquals(targetSong.id, songs[0].id)
    }

    @Test
    fun testAntiClusteringEffectiveness() {
        // 40 songs across 4 artists (10 songs per artist)
        val songs = (1L..40L).map { id ->
            val artistId = (id - 1) / 10 // Artists 0, 1, 2, 3
            createDummySong(id = id, artistId = artistId, albumId = artistId)
        }.toMutableList()

        val shuffledSongs = ArrayList(songs)
        EnhancedShuffleEngine.shuffle(shuffledSongs, current = -1, customSeed = 88888L)

        var collisions = 0
        for (i in 0 until shuffledSongs.size - 1) {
            if (shuffledSongs[i].artistId == shuffledSongs[i + 1].artistId) {
                collisions++
            }
        }

        // With 4 artists (10 songs each), random chance of same-artist collision is ~25%.
        // Anti-clustering buffer should drastically reduce collisions (typically <= 2 or 3 total collisions).
        assertTrue("Expected low artist collisions in enhanced shuffle, got $collisions", collisions <= 4)
    }

    @Test
    fun testSaturatedArtistLimit() {
        // 20 songs, 18 from Artist 1 (90% saturation)
        val songs = (1L..20L).map { id ->
            val artistId = if (id <= 18) 1L else id
            createDummySong(id = id, artistId = artistId, albumId = artistId)
        }.toMutableList()

        EnhancedShuffleEngine.shuffle(songs, current = -1, customSeed = 77777L)

        assertEquals(20, songs.size)
        assertEquals(20, songs.map { it.id }.toSet().size)
    }
}
