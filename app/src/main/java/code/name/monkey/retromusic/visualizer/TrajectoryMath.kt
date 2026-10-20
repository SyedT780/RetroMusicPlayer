/*
 * Copyright (c) 2025 Retro Music Player.
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

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

data class ShuffleVisualRecord(
    val runId: Int,
    val mode: String,
    val trackCount: Int,
    val artistCollisions: Int,
    val sameSlotMatchText: String,
    val repeatedTransText: String,
    val repetitivenessScore: String,
    val permutedTrackIndices: IntArray,
    val manualPlayNextIndices: List<Int> = emptyList()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ShuffleVisualRecord

        if (runId != other.runId) return false
        if (mode != other.mode) return false
        if (trackCount != other.trackCount) return false
        if (artistCollisions != other.artistCollisions) return false
        if (sameSlotMatchText != other.sameSlotMatchText) return false
        if (repeatedTransText != other.repeatedTransText) return false
        if (repetitivenessScore != other.repetitivenessScore) return false
        if (!permutedTrackIndices.contentEquals(other.permutedTrackIndices)) return false
        if (manualPlayNextIndices != other.manualPlayNextIndices) return false

        return true
    }

    override fun hashCode(): Int {
        var result = runId
        result = 31 * result + mode.hashCode()
        result = 31 * result + trackCount
        result = 31 * result + artistCollisions
        result = 31 * result + sameSlotMatchText.hashCode()
        result = 31 * result + repeatedTransText.hashCode()
        result = 31 * result + repetitivenessScore.hashCode()
        result = 31 * result + permutedTrackIndices.contentHashCode()
        result = 31 * result + manualPlayNextIndices.hashCode()
        return result
    }
}

class TrajectoryPath(
    val x: FloatArray,
    val y: FloatArray,
    val minX: Float,
    val maxX: Float,
    val minY: Float,
    val maxY: Float
) {
    val size: Int get() = x.size
}

object TrajectoryMath {
    /**
     * Projects a permutation into a closed Roots-of-Unity random walk.
     * Guaranteed: (x[N], y[N]) == (0f, 0f).
     */
    fun computeWalk(perm: IntArray): TrajectoryPath {
        val n = perm.size
        val x = FloatArray(n + 1)
        val y = FloatArray(n + 1)
        var curX = 0f
        var curY = 0f
        var minX = 0f
        var maxX = 0f
        var minY = 0f
        var maxY = 0f

        val twoPi = 2.0 * Math.PI
        for (t in 0 until n) {
            val theta = twoPi * perm[t].toDouble() / n.toDouble()
            curX += cos(theta).toFloat()
            curY += sin(theta).toFloat()
            x[t + 1] = curX
            y[t + 1] = curY

            if (curX < minX) minX = curX
            if (curX > maxX) maxX = curX
            if (curY < minY) minY = curY
            if (curY > maxY) maxY = curY
        }
        return TrajectoryPath(x, y, minX, maxX, minY, maxY)
    }
}
