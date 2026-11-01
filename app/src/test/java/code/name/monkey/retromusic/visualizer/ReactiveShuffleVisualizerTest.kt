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

package code.name.monkey.retromusic.visualizer

import code.name.monkey.retromusic.extensions.computeInvariantHash
import code.name.monkey.retromusic.extensions.computeInvariantRanks
import code.name.monkey.retromusic.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ReactiveShuffleVisualizerTest {

    private fun createDummySong(id: Long, title: String, artist: String, album: String): Song {
        return Song(
            id = id,
            title = title,
            trackNumber = 1,
            year = 2024,
            duration = 180000,
            data = "/path/to/song_$id.mp3",
            dateModified = System.currentTimeMillis(),
            albumId = id,
            albumName = album,
            artistId = id,
            artistName = artist,
            composer = "",
            albumArtist = ""
        )
    }

    @Before
    fun setUp() {
        ReactiveShuffleVisualizer.instance.resetHistory()
    }

    @Test
    fun testMurmurHash3Determinism() {
        val song1 = createDummySong(1L, "Title A", "Artist A", "Album A")
        val song2 = createDummySong(1L, "Title A", "Artist A", "Album A")
        val song3 = createDummySong(2L, "Title B", "Artist B", "Album B")

        val hash1 = song1.computeInvariantHash()
        val hash2 = song2.computeInvariantHash()
        val hash3 = song3.computeInvariantHash()

        assertEquals("Hashes for identical metadata must be equal", hash1, hash2)
        assertTrue("Hashes for different metadata should differ", hash1 != hash3)
    }

    @Test
    fun testInvariantRanksCalculation() {
        val songs = listOf(
            createDummySong(10L, "Zebra", "Artist Z", "Album Z"),
            createDummySong(20L, "Alpha", "Artist A", "Album A"),
            createDummySong(30L, "Beta", "Artist B", "Album B")
        )

        val rankMap = songs.computeInvariantRanks()
        assertEquals(3, rankMap.size)
        assertTrue("All song IDs must be mapped to zero-based ranks", rankMap.keys.containsAll(listOf(10L, 20L, 30L)))

        val ranks = rankMap.values.toSet()
        assertEquals(setOf(0, 1, 2), ranks)
    }

    @Test
    fun testRootsOfUnityClosureProperty() {
        val visualizer = ReactiveShuffleVisualizer(historyDepthK = 4, slotToleranceDelta = 0)
        val songs = (1L..20L).map { id -> createDummySong(id, "Track $id", "Artist $id", "Album $id") }

        // A full permutation of 20 distinct tracks must form a mathematically closed loop
        val frame = visualizer.computeFrame(songs)
        assertTrue("Full unmutated permutation loop must be mathematically closed at (0,0)", frame.isClosedLoop)

        // Mutating queue by adding a duplicate song breaks closure
        val mutatedQueue = songs + songs.first()
        val mutatedFrame = visualizer.computeFrame(mutatedQueue, totalLibraryN = songs.size)
        assertFalse("Queue mutation with duplicate item must break mathematical closure", mutatedFrame.isClosedLoop)
    }

    @Test
    fun testSameSlotCollisionDetection() {
        val visualizer = ReactiveShuffleVisualizer(historyDepthK = 4, slotToleranceDelta = 0)
        val songs = (1L..10L).map { id -> createDummySong(id, "Track $id", "Artist $id", "Album $id") }

        // Record initial shuffle run
        visualizer.recordShuffleRun(songs)

        // Second run where song 0 and song 5 remain at slot 0 and slot 5
        val run2 = ArrayList(songs)
        // Swap slots 1 and 2
        val temp = run2[1]
        run2[1] = run2[2]
        run2[2] = temp

        val frame2 = visualizer.computeFrame(run2)

        assertTrue("Slot 0 should detect same-slot collision with previous run", frame2.nodes[0].isCollision)
        assertFalse("Slot 1 should NOT collide after position swap", frame2.nodes[1].isCollision)
        assertFalse("Slot 2 should NOT collide after position swap", frame2.nodes[2].isCollision)
        assertTrue("Slot 5 should detect same-slot collision with previous run", frame2.nodes[5].isCollision)
    }

    @Test
    fun testHistoryDepthEviction() {
        val visualizer = ReactiveShuffleVisualizer(historyDepthK = 2, slotToleranceDelta = 0)
        val songs = (1L..5L).map { id -> createDummySong(id, "Track $id", "Artist $id", "Album $id") }

        // Record Run 1
        visualizer.recordShuffleRun(songs)

        // Record Run 2
        visualizer.recordShuffleRun(songs)

        // Record Run 3 (evicts Run 1, so only Run 2 & Run 3 are kept)
        val reversed = songs.reversed()
        visualizer.recordShuffleRun(reversed)

        // Compute frame for original songs order
        val frame = visualizer.computeFrame(songs)

        // Slot 2 in 'songs' is track 3 (middle track). In 'reversed', middle track is also track 3 (slot 2).
        assertTrue("Slot 2 should collide with Run 3", frame.nodes[2].isCollision)
    }
}
