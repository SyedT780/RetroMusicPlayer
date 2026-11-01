/*
 * Copyright (c) 2026 Retro Music Player.
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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnhancedStrictTailShuffleEngineTest {

    private fun createTrack(id: String, invariantId: Int, artistId: Long, albumId: Long): ShuffleTrack {
        return ShuffleTrack(
            id = id,
            invariantId = invariantId,
            artistId = artistId,
            albumId = albumId,
            title = "Track $id"
        )
    }

    @Test
    fun testAntiBigramRepeatPenalty() {
        val engine = EnhancedStrictTailShuffleEngine(cooldownDepth = 4, windowCapacity = 8)
        val domain = listOf(
            createTrack("t0", 0, artistId = 1, albumId = 1),
            createTrack("t1", 1, artistId = 2, albumId = 2),
            createTrack("t2", 2, artistId = 3, albumId = 3),
            createTrack("t3", 3, artistId = 4, albumId = 4)
        )

        // Previous run had transition (0 -> 1)
        val previousBigrams = setOf(Pair(0, 1))

        // Force pinned start at index 0 (track t0)
        val result = engine.generateQueue(
            domain = domain,
            currentTrackIndex = 0,
            persistentPlayedHistory = emptySet(),
            previousRunBigrams = previousBigrams,
            customSeed = 42L
        )

        assertEquals("t0", result[0].id)
        // Track t1 (invId 1) should be penalized by +25.0 penalty after t0 (invId 0), so t1 should NOT be placed immediately at slot 1
        val slot1InvId = result[1].invariantId
        assertTrue("Track forming repeated bigram (0 -> 1) should be penalized and not placed at slot 1", slot1InvId != 1)
    }

    @Test
    fun testStrictTailQuarantineForPlayedTracks() {
        val engine = EnhancedStrictTailShuffleEngine(cooldownDepth = 4, windowCapacity = 8)
        val domain = (0..9).map { i ->
            createTrack("t$i", i, artistId = i.toLong(), albumId = i.toLong())
        }

        // Played tracks: t0, t1, t2
        val persistentPlayedHistory = setOf("t0", "t1", "t2")

        val result = engine.generateQueue(
            domain = domain,
            currentTrackIndex = -1,
            persistentPlayedHistory = persistentPlayedHistory,
            previousRunBigrams = emptySet(),
            customSeed = 100L
        )

        assertEquals(10, result.size)

        // Front 7 slots must contain unplayed tracks (t3..t9)
        val frontTierIds = result.subList(0, 7).map { it.id }.toSet()
        val tailTierIds = result.subList(7, 10).map { it.id }.toSet()

        val expectedUnplayed = (3..9).map { "t$it" }.toSet()
        val expectedPlayed = setOf("t0", "t1", "t2")

        assertEquals("Front tier slots 0..6 must contain only unplayed tracks", expectedUnplayed, frontTierIds)
        assertEquals("Tail tier slots 7..9 must quarantine played tracks", expectedPlayed, tailTierIds)
    }

    @Test
    fun testDynamicJitterVariability() {
        val engine = EnhancedStrictTailShuffleEngine()
        val domain = (0..19).map { i ->
            createTrack("t$i", i, artistId = i.toLong(), albumId = i.toLong())
        }

        val run1 = engine.generateQueue(domain, -1, emptySet(), emptySet(), customSeed = 111L)
        val run2 = engine.generateQueue(domain, -1, emptySet(), emptySet(), customSeed = 999L)

        val run1Ids = run1.map { it.id }
        val run2Ids = run2.map { it.id }

        assertFalse("Different session seeds must produce non-identical queue orderings due to dynamic jitter", run1Ids == run2Ids)
    }
}
