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
    fun testSelectZeroAndSelectOne() {
        // N = 10 tracks
        // Bitset: bits 0, 2, 5 are set to 1 (played), rest are 0 (unplayed)
        val n = 10
        val bitset = longArrayOf((1L shl 0) or (1L shl 2) or (1L shl 5))

        // Played indices (ones): 0, 2, 5 (M = 3)
        // Unplayed indices (zeros): 1, 3, 4, 6, 7, 8, 9 (U = 7)

        assertEquals(0, EnhancedShuffleEngine.selectOne(0, bitset, n))
        assertEquals(2, EnhancedShuffleEngine.selectOne(1, bitset, n))
        assertEquals(5, EnhancedShuffleEngine.selectOne(2, bitset, n))

        assertEquals(1, EnhancedShuffleEngine.selectZero(0, bitset, n))
        assertEquals(3, EnhancedShuffleEngine.selectZero(1, bitset, n))
        assertEquals(4, EnhancedShuffleEngine.selectZero(2, bitset, n))
        assertEquals(6, EnhancedShuffleEngine.selectZero(3, bitset, n))
        assertEquals(7, EnhancedShuffleEngine.selectZero(4, bitset, n))
        assertEquals(8, EnhancedShuffleEngine.selectZero(5, bitset, n))
        assertEquals(9, EnhancedShuffleEngine.selectZero(6, bitset, n))
    }

    @Test
    fun testResolveTrackIndexPartitioning() {
        val n = 10
        val bitset = longArrayOf((1L shl 0) or (1L shl 2) or (1L shl 5)) // 3 played, 7 unplayed
        val m = 3
        val u = 7
        val sUnplayed = 100L
        val sPlayed = 200L

        val unplayedIndices = setOf(1, 3, 4, 6, 7, 8, 9)
        val playedIndices = setOf(0, 2, 5)

        // Tier 1 (i < U): Draws from unplayed indices
        for (i in 0 until u) {
            val trackIdx = EnhancedShuffleEngine.resolveTrackIndex(i, n, m, u, sUnplayed, sPlayed, bitset)
            assertTrue("Tier 1 index $trackIdx should be unplayed", unplayedIndices.contains(trackIdx))
        }

        // Tier 2 (i >= U): Draws from played indices
        for (i in u until n) {
            val trackIdx = EnhancedShuffleEngine.resolveTrackIndex(i, n, m, u, sUnplayed, sPlayed, bitset)
            assertTrue("Tier 2 index $trackIdx should be played", playedIndices.contains(trackIdx))
        }
    }

    @Test
    fun testCalculatePenaltyAndTieBreaker() {
        val candidate = createDummySong(id = 1L, artistId = 100L, albumId = 10L)
        val cooldown = listOf(
            EnhancedShuffleEngine.AttributeDescriptor(artistId = 100L, albumId = 10L), // p = 0
            EnhancedShuffleEngine.AttributeDescriptor(artistId = 200L, albumId = 20L)  // p = 1
        )
        val cooldownDepth = 3

        val penalty = EnhancedShuffleEngine.calculatePenalty(
            candidate = candidate,
            cooldown = cooldown,
            cooldownDepth = cooldownDepth,
            weightArtist = 10.0,
            weightAlbum = 0.0,
            seed = 12345L,
            streamIndex = 0,
            slotIndex = 0
        )

        // Artist recency penalty R(artist) = cooldownDepth - p = 3 - 0 = 3
        // Base artist penalty = 10.0 * 3 = 30.0
        // Eta tie breaker is in range [0.0, 0.495]
        assertTrue("Penalty $penalty should be >= 30.0 due to artist collision in cooldown", penalty >= 30.0)
        assertTrue("Penalty $penalty should be < 30.5", penalty < 30.5)
    }

    @Test
    fun testTwoTierUnplayedPrioritizationAndTailPushing() {
        val songs = (0L..9L).map { createDummySong(id = it, artistId = it, albumId = it) }.toMutableList()

        // Initial shuffle: Dispatches all 10 songs
        EnhancedShuffleEngine.shuffle(songs, current = -1, customSeed = 1111L)
        assertEquals(10, EnhancedShuffleEngine.getM())
        assertEquals(0, EnhancedShuffleEngine.getU())

        // Now shuffle again: Since U was 0, macroCycleReset clears bitset (M=0, U=10)
        EnhancedShuffleEngine.shuffle(songs, current = -1, customSeed = 2222L)
        assertEquals(10, EnhancedShuffleEngine.getM())
        assertEquals(0, EnhancedShuffleEngine.getU())
    }

    @Test
    fun testMacroCycleReset() {
        val n = 20
        EnhancedShuffleEngine.macroCycleReset(n)

        assertEquals(20, EnhancedShuffleEngine.getDomainSize())
        assertEquals(0, EnhancedShuffleEngine.getM())
        assertEquals(20, EnhancedShuffleEngine.getU())

        val bitset = EnhancedShuffleEngine.getBitset()
        for (word in bitset) {
            assertEquals(0L, word)
        }
    }

    @Test
    fun testCooldownPreservationAcrossReshuffle() {
        val songs = (1L..10L).map { createDummySong(id = it, artistId = it % 3, albumId = it % 2) }.toMutableList()

        EnhancedShuffleEngine.shuffle(songs, current = 0, customSeed = 3333L)
        val cooldownBefore = EnhancedShuffleEngine.getCooldown()
        assertTrue("Cooldown should not be empty after shuffle", cooldownBefore.isNotEmpty())

        EnhancedShuffleEngine.shuffle(songs, current = -1, customSeed = 4444L)
        val cooldownAfter = EnhancedShuffleEngine.getCooldown()
        assertTrue("Cooldown should be preserved across reshuffle", cooldownAfter.isNotEmpty())
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
