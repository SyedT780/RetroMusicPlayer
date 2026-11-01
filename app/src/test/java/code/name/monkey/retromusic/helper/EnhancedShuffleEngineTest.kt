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

import android.content.SharedPreferences
import code.name.monkey.retromusic.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EnhancedShuffleEngineTest {

    @Before
    fun setUp() {
        val fakePrefs = FakeSharedPreferences()
        EnhancedShuffleEngine.clearPersistentPlayedHistory(fakePrefs)
    }

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
    fun testReadOnlyQueueGeneration() {
        val fakePrefs = FakeSharedPreferences()
        EnhancedShuffleEngine.clearPersistentPlayedHistory(fakePrefs)

        val songs = (0L..9L).map { createDummySong(id = it, artistId = it, albumId = it) }.toMutableList()

        // Generate queue
        EnhancedShuffleEngine.shuffle(songs, current = -1, customSeed = 1111L)

        // Generating a queue must NOT add tracks to persistentPlayedHistory
        val history = EnhancedShuffleEngine.getPersistentPlayedHistory()
        assertTrue("Queue generation must be read-only and not mutate persistentPlayedHistory", history.isEmpty())
        assertEquals(0, EnhancedShuffleEngine.getM())
        assertEquals(10, EnhancedShuffleEngine.getU())
    }

    @Test
    fun testStrictTailQuarantineForPlayedTracks() {
        val fakePrefs = FakeSharedPreferences()
        val songs = (0L..9L).map { createDummySong(id = it, artistId = it, albumId = it) }.toMutableList()

        EnhancedShuffleEngine.macroCycleReset(songs.size, fakePrefs)

        // Mark songs 0, 1, 2 as played
        EnhancedShuffleEngine.markTrackPlayed("0", fakePrefs)
        EnhancedShuffleEngine.markTrackPlayed("1", fakePrefs)
        EnhancedShuffleEngine.markTrackPlayed("2", fakePrefs)

        assertEquals(3, EnhancedShuffleEngine.getM())
        assertEquals(7, EnhancedShuffleEngine.getU())

        val shuffled = ArrayList(songs)
        EnhancedShuffleEngine.shuffle(shuffled, current = -1, customSeed = 12345L)

        assertEquals(10, shuffled.size)

        // Front 7 slots (0 to 6) MUST be unplayed tracks (IDs 3..9)
        val frontTierIds = shuffled.subList(0, 7).map { it.id }.toSet()
        val tailTierIds = shuffled.subList(7, 10).map { it.id }.toSet()

        val expectedUnplayed = setOf(3L, 4L, 5L, 6L, 7L, 8L, 9L)
        val expectedPlayed = setOf(0L, 1L, 2L)

        assertEquals("Front tier slots 0..6 must contain only unplayed tracks", expectedUnplayed, frontTierIds)
        assertEquals("Tail tier slots 7..9 must quarantine played tracks", expectedPlayed, tailTierIds)
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

        assertTrue("Penalty $penalty should be >= 30.0 due to artist collision in cooldown", penalty >= 30.0)
        assertTrue("Penalty $penalty should be < 30.5", penalty < 30.5)
    }

    @Test
    fun testMacroCycleReset() {
        val fakePrefs = FakeSharedPreferences()
        val n = 20
        EnhancedShuffleEngine.macroCycleReset(n, fakePrefs)

        assertEquals(20, EnhancedShuffleEngine.getDomainSize())
        assertEquals(0, EnhancedShuffleEngine.getM())
        assertEquals(20, EnhancedShuffleEngine.getU())
        assertTrue(EnhancedShuffleEngine.getPersistentPlayedHistory().isEmpty())
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
    fun testStatePersistenceAndRestoration() {
        val fakePrefs = FakeSharedPreferences()
        val songs = (1L..10L).map { createDummySong(id = it, artistId = it % 3, albumId = it % 2) }.toMutableList()

        EnhancedShuffleEngine.markTrackPlayed("1", fakePrefs)
        EnhancedShuffleEngine.markTrackPlayed("2", fakePrefs)
        EnhancedShuffleEngine.shuffle(songs, current = 0, customSeed = 12345L)
        EnhancedShuffleEngine.saveState(fakePrefs)

        val domainBefore = EnhancedShuffleEngine.getDomainSize()
        val mBefore = EnhancedShuffleEngine.getM()
        val uBefore = EnhancedShuffleEngine.getU()
        val historyBefore = EnhancedShuffleEngine.getPersistentPlayedHistory()

        EnhancedShuffleEngine.restoreState(fakePrefs)

        assertEquals(domainBefore, EnhancedShuffleEngine.getDomainSize())
        assertEquals(mBefore, EnhancedShuffleEngine.getM())
        assertEquals(uBefore, EnhancedShuffleEngine.getU())
        assertEquals(historyBefore, EnhancedShuffleEngine.getPersistentPlayedHistory())
    }

    private class FakeSharedPreferences : SharedPreferences {
        private val map = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = map
        override fun getString(key: String?, defValue: String?): String? = (map[key] as? String) ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = (map[key] as? MutableSet<String>) ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = (map[key] as? Int) ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = (map[key] as? Long) ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = (map[key] as? Float) ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = (map[key] as? Boolean) ?: defValue
        override fun contains(key: String?): Boolean = map.containsKey(key)
        override fun edit(): SharedPreferences.Editor = EditorImpl()
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        private inner class EditorImpl : SharedPreferences.Editor {
            override fun putString(key: String?, value: String?): SharedPreferences.Editor { map[key!!] = value; return this }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor { map[key!!] = values; return this }
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor { map[key!!] = value; return this }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor { map[key!!] = value; return this }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor { map[key!!] = value; return this }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor { map[key!!] = value; return this }
            override fun remove(key: String?): SharedPreferences.Editor { map.remove(key); return this }
            override fun clear(): SharedPreferences.Editor { map.clear(); return this }
            override fun commit(): Boolean = true
            override fun apply() {}
        }
    }
}
