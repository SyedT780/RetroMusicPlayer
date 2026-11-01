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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DualLayerShuffleVisualizerTest {

    private fun createDummyTrack(id: String, invariantId: Int, title: String, artist: String): TrackItem {
        return TrackItem(
            id = id,
            invariantId = invariantId,
            title = title,
            artist = artist
        )
    }

    @Test
    fun testGhostBaselineImmutabilityOnManualQueueEdit() {
        val totalN = 10
        val visualizer = DualLayerShuffleVisualizer(totalLibraryN = totalN)
        val initialQueue = (0 until totalN).map { i ->
            createDummyTrack(id = "track_$i", invariantId = i, title = "Track $i", artist = "Artist $i")
        }

        // 1. Trigger initial shuffle
        visualizer.onNewShuffleTriggered(initialQueue, startingIndex = 0)
        val initialFrame = visualizer.computeLiveFrame(initialQueue)

        assertEquals("When unmutated, baseline points count equals live points count", initialFrame.baselinePoints.size, initialFrame.livePoints.size)
        assertEquals("When unmutated, baseline points match live points exactly", initialFrame.baselinePoints, initialFrame.livePoints)

        // 2. Simulate manual queue edit (reorder tracks)
        val mutatedQueue = initialQueue.toMutableList().apply {
            val temp = removeAt(0)
            add(temp)
        }

        val editedFrame = visualizer.computeLiveFrame(mutatedQueue)

        // Baseline points must remain completely static and frozen
        assertEquals("Ghost baseline must remain static during manual edits", initialFrame.baselinePoints, editedFrame.baselinePoints)
        // Live flow path must deflect away from baseline
        assertNotEquals("Live flow path must deflect away from baseline when modified", editedFrame.baselinePoints, editedFrame.livePoints)
    }

    @Test
    fun testPlaybackTruncationOnReshuffle() {
        val totalN = 10
        val visualizer = DualLayerShuffleVisualizer(totalLibraryN = totalN)
        val session1Queue = (0 until totalN).map { i ->
            createDummyTrack(id = "track_$i", invariantId = i, title = "Track $i", artist = "Artist $i")
        }

        // Session 1: Start shuffle
        visualizer.onNewShuffleTriggered(session1Queue, startingIndex = 0)

        // Play 3 tracks (cursor at index 2 => played tracks 0, 1, 2)
        visualizer.onPlaybackCursorChanged(newCursorIndex = 2)

        // Session 2: Trigger reshuffle
        val session2Queue = session1Queue.shuffled()
        visualizer.onNewShuffleTriggered(session2Queue, startingIndex = 0)

        val frameSession2 = visualizer.computeLiveFrame(session2Queue)

        // Unplayed tracks (invariantIds 3..9) from Session 1 must not trigger phantom collisions
        for (node in frameSession2.liveNodes) {
            val trackInvId = session2Queue[node.slot].invariantId
            if (trackInvId >= 3) {
                assertFalse(
                    "Unplayed tracks from prior session (invId >= 3) must never trigger phantom collisions",
                    node.isCollisionWithPrevious
                )
            }
        }
    }

    @Test
    fun testImmediatePreviousOverlapCollisionDetection() {
        val totalN = 5
        val visualizer = DualLayerShuffleVisualizer(totalLibraryN = totalN, slotToleranceDelta = 0)

        val session1Queue = (0 until totalN).map { i ->
            createDummyTrack(id = "track_$i", invariantId = i, title = "Track $i", artist = "Artist $i")
        }

        visualizer.onNewShuffleTriggered(session1Queue, startingIndex = 0)
        // All 5 tracks played in session 1
        visualizer.onPlaybackCursorChanged(newCursorIndex = 4)

        // Session 2: slot 0 and slot 3 have same invariantId as session 1, others swapped
        val session2Queue = listOf(
            session1Queue[0], // Same track at slot 0 -> COLLISION
            session1Queue[2], // Different track at slot 1
            session1Queue[1], // Different track at slot 2
            session1Queue[3], // Same track at slot 3 -> COLLISION
            session1Queue[4]  // Same track at slot 4 -> COLLISION
        )

        visualizer.onNewShuffleTriggered(session2Queue, startingIndex = 0)
        val frame = visualizer.computeLiveFrame(session2Queue)

        assertTrue("Slot 0 should detect collision with immediate previous run", frame.liveNodes[0].isCollisionWithPrevious)
        assertFalse("Slot 1 should NOT detect collision after swap", frame.liveNodes[1].isCollisionWithPrevious)
        assertFalse("Slot 2 should NOT detect collision after swap", frame.liveNodes[2].isCollisionWithPrevious)
        assertTrue("Slot 3 should detect collision with immediate previous run", frame.liveNodes[3].isCollisionWithPrevious)
        assertTrue("Slot 4 should detect collision with immediate previous run", frame.liveNodes[4].isCollisionWithPrevious)
    }

    @Test
    fun testMathematicalLoopClosure() {
        val totalN = 20
        val visualizer = DualLayerShuffleVisualizer(totalLibraryN = totalN)

        val fullQueue = (0 until totalN).map { i ->
            createDummyTrack(id = "track_$i", invariantId = i, title = "Track $i", artist = "Artist $i")
        }

        visualizer.onNewShuffleTriggered(fullQueue)
        val frameUnmutated = visualizer.computeLiveFrame(fullQueue)
        assertTrue("Complete untouched permutation must form a mathematically closed loop", frameUnmutated.isClosedLoop)

        // Remove one track to break full permutation
        val incompleteQueue = fullQueue.drop(1)
        val frameMutated = visualizer.computeLiveFrame(incompleteQueue)
        assertFalse("Incomplete queue must break mathematical loop closure", frameMutated.isClosedLoop)
    }
}
