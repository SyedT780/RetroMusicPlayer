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

import code.name.monkey.retromusic.extensions.computeInvariantRanks
import code.name.monkey.retromusic.model.Song
import code.name.monkey.retromusic.visualizer.DualLayerShuffleVisualizer.Companion.toTrackItems
import java.util.ArrayDeque
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

data class VisualizerFrame(
    val nodes: List<VisualizerNode>,
    val pathPoints: List<PointF>,
    val isClosedLoop: Boolean
)

/**
 * Reactive Permutation Collision Visualizer Engine
 *
 * Inverted-index history engine with O(1) slot lookups for real-time
 * coordinate trajectory computation and same-slot collision detection.
 * Delegates to DualLayerShuffleVisualizer for dual-layer real-time visualizer rendering.
 */
class ReactiveShuffleVisualizer(
    private val historyDepthK: Int = 4,
    private val slotToleranceDelta: Int = 0
) {
    // Inverted index for O(1) history lookups: InvariantTrackId -> List of slots occupied
    private val historicalSlotIndex = HashMap<Int, ArrayList<Int>>()
    private val runSnapshots = ArrayDeque<List<Int>>()

    val dualLayerEngine = DualLayerShuffleVisualizer(slotToleranceDelta = slotToleranceDelta)

    @Synchronized
    fun resetHistory() {
        historicalSlotIndex.clear()
        runSnapshots.clear()
        dualLayerEngine.resetHistory()
    }

    /**
     * Records a freshly shuffled queue to persistent history and updates dual layer engine.
     */
    @Synchronized
    fun recordShuffleRun(queue: List<Song>, startingIndex: Int = 0) {
        if (queue.isEmpty()) return

        val rankMap = queue.computeInvariantRanks()
        val snapshot = queue.map { rankMap[it.id] ?: 0 }

        if (runSnapshots.size >= historyDepthK) {
            val oldestRun = runSnapshots.removeFirst()
            for ((slot, invId) in oldestRun.withIndex()) {
                val slots = historicalSlotIndex[invId]
                slots?.remove(slot)
                if (slots?.isEmpty() == true) {
                    historicalSlotIndex.remove(invId)
                }
            }
        }

        runSnapshots.addLast(snapshot)

        for ((slot, invId) in snapshot.withIndex()) {
            historicalSlotIndex.getOrPut(invId) { ArrayList() }.add(slot)
        }

        dualLayerEngine.onNewShuffleTriggered(queue.toTrackItems(), startingIndex)
    }

    @Synchronized
    fun onPlaybackCursorChanged(newCursorIndex: Int) {
        dualLayerEngine.onPlaybackCursorChanged(newCursorIndex)
    }

    @Synchronized
    fun computeDualLayerFrame(currentQueue: List<Song>): DualLayerFrame {
        return dualLayerEngine.computeLiveFrame(currentQueue.toTrackItems())
    }

    /**
     * Real-time calculation called whenever the queue is generated,
     * reordered, edited, or augmented with manual additions.
     */
    @Synchronized
    fun computeFrame(
        currentQueue: List<Song>,
        totalLibraryN: Int = currentQueue.size
    ): VisualizerFrame {
        val nodeCount = currentQueue.size
        if (nodeCount == 0) {
            return VisualizerFrame(
                nodes = emptyList(),
                pathPoints = listOf(PointF(0f, 0f)),
                isClosedLoop = true
            )
        }

        val rankMap = currentQueue.computeInvariantRanks()
        val nodes = ArrayList<VisualizerNode>(nodeCount)
        val pathPoints = ArrayList<PointF>(nodeCount + 1)

        var curX = 0.0f
        var curY = 0.0f
        pathPoints.add(PointF(curX, curY))

        val effectiveN = maxOf(1, totalLibraryN)
        val angleStep = (2.0 * PI) / effectiveN.toDouble()

        for (slot in 0 until nodeCount) {
            val song = currentQueue[slot]
            val invId = rankMap[song.id] ?: slot

            val theta = invId * angleStep
            curX += cos(theta).toFloat()
            curY += sin(theta).toFloat()
            val nodePos = PointF(curX, curY)
            pathPoints.add(nodePos)

            val pastSlots = historicalSlotIndex[invId]
            val matchedSlots = ArrayList<Int>()

            if (pastSlots != null) {
                for (pastSlot in pastSlots) {
                    if (abs(pastSlot - slot) <= slotToleranceDelta) {
                        matchedSlots.add(pastSlot)
                    }
                }
            }

            nodes.add(
                VisualizerNode(
                    slot = slot,
                    trackId = song.id.toString(),
                    position = nodePos,
                    isCollisionWithPrevious = matchedSlots.isNotEmpty()
                )
            )
        }

        val isClosed = (curX * curX + curY * curY) < 0.01f

        return VisualizerFrame(
            nodes = nodes,
            pathPoints = pathPoints,
            isClosedLoop = isClosed
        )
    }

    companion object {
        val instance = ReactiveShuffleVisualizer()
    }
}
