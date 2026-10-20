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

import android.graphics.Color
import android.graphics.Path
import android.graphics.PointF
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// ============================================================================
// 1. DATA STRUCTURES & MODELS
// ============================================================================

/**
 * Deterministic lateral branchlet sprouting from a hyphal node.
 */
data class BranchOffset(
    val srcIdx: Int,
    val bx: Float,
    val by: Float,
    val depthSeed: Float
)

/**
 * Geometric trajectory path in normalized unit space.
 * Origin (0,0) is mathematically both the start (index 0) and end (index size-1).
 */
data class TrajectoryPath(
    val x: FloatArray,
    val y: FloatArray,
    val maxRadius: Float,
    val size: Int,
    val branchOffsets: List<BranchOffset> = emptyList()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as TrajectoryPath
        return x.contentEquals(other.x) && y.contentEquals(other.y) && maxRadius == other.maxRadius
    }

    override fun hashCode(): Int {
        var result = x.contentHashCode()
        result = 31 * result + y.contentHashCode()
        result = 31 * result + maxRadius.hashCode()
        return result
    }
}

/**
 * Visual record of a single shuffle run, persisted for strata history.
 */
data class ShuffleVisualRecord(
    val runId: Long = System.currentTimeMillis(),
    val permutedTrackIndices: IntArray = intArrayOf(),
    val walk: TrajectoryPath = TrajectoryMath.computeWalk(permutedTrackIndices),
    val playNextIndices: Set<Int> = emptySet(),
    val mode: String = "",
    val trackCount: Int = 0,
    val artistCollisions: Int = 0,
    val sameSlotMatchText: String = "",
    val repeatedTransText: String = "",
    val repetitivenessScore: String = "",
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
        if (playNextIndices != other.playNextIndices) return false
        if (walk != other.walk) return false

        return true
    }

    override fun hashCode(): Int {
        var result = runId.hashCode()
        result = 31 * result + mode.hashCode()
        result = 31 * result + trackCount
        result = 31 * result + artistCollisions
        result = 31 * result + sameSlotMatchText.hashCode()
        result = 31 * result + repeatedTransText.hashCode()
        result = 31 * result + repetitivenessScore.hashCode()
        result = 31 * result + permutedTrackIndices.contentHashCode()
        result = 31 * result + manualPlayNextIndices.hashCode()
        result = 31 * result + playNextIndices.hashCode()
        result = 31 * result + walk.hashCode()
        return result
    }
}

/**
 * Living transverse filament connecting two closely correlated trajectories.
 */
data class AnastomosisBridge(
    val step: Int,
    val ax: Float,
    val ay: Float,
    val bx: Float,
    val by: Float,
    val dist: Float
)

/**
 * Result of entropy collision analysis between two trajectories.
 */
data class TwinAffinityResult(
    val similarityPercentage: Float, // 0.0f .. 100.0f
    val rmsd: Float,
    val matchedGeneration: Int,
    val fusedBridges: List<AnastomosisBridge>
)

// ============================================================================
// 2. MATHEMATICAL TRAJECTORY ENGINE
// ============================================================================

object TrajectoryMath {

    /**
     * Projects permutation pi into a closed organic Roots-of-Unity Random Walk.
     * Guaranteed to close at (0, 0) because sum of e^(i * 2pi * k / N) for a bijection is 0.
     */
    fun computeWalk(perm: IntArray): TrajectoryPath {
        val n = perm.size
        if (n == 0) {
            return TrajectoryPath(
                x = floatArrayOf(0f),
                y = floatArrayOf(0f),
                maxRadius = 1.0f,
                size = 1,
                branchOffsets = emptyList()
            )
        }

        val x = FloatArray(n + 1)
        val y = FloatArray(n + 1)
        var curX = 0f
        var curY = 0f
        var maxRadius = 0f

        val twoPi = 2.0 * Math.PI
        for (t in 0 until n) {
            val theta = (twoPi * perm[t]) / n
            curX += cos(theta).toFloat()
            curY += sin(theta).toFloat()
            x[t + 1] = curX
            y[t + 1] = curY

            val r = hypot(curX, curY)
            if (r > maxRadius) maxRadius = r
        }

        // Enforce mathematical closure
        x[n] = 0.0f
        y[n] = 0.0f

        // Deterministic generative micro-branchlets (lateral hyphae)
        val branches = ArrayList<BranchOffset>()
        for (i in 2 until n step 3) {
            val dx = x[i] - x[i - 1]
            val dy = y[i] - y[i - 1]
            val perpAngle = atan2(dy, dx) + (Math.PI.toFloat() / 2f) * if ((i % 2) == 0) 1f else -1f
            val branchLen = 0.45f + (((i * 37) % 100) / 100f) * 0.75f
            branches.add(
                BranchOffset(
                    srcIdx = i,
                    bx = x[i] + cos(perpAngle) * branchLen,
                    by = y[i] + sin(perpAngle) * branchLen,
                    depthSeed = ((i * 17) % 100) / 100f
                )
            )
        }

        return TrajectoryPath(
            x = x,
            y = y,
            maxRadius = maxRadius.coerceAtLeast(1.0f),
            size = n + 1,
            branchOffsets = branches
        )
    }

    /**
     * Anastomosis and Twin Affinity Scanner:
     * Compares active walk against a historical walk. High similarity (>= 80%)
     * indicates algorithmic lock-in, PRNG seed collision, or low entropy.
     */
    fun computeTwinAffinity(
        activeWalk: TrajectoryPath,
        historicalWalk: TrajectoryPath,
        genIndex: Int,
        fusionThresholdDistance: Float = 1.65f
    ): TwinAffinityResult {
        if (activeWalk.size != historicalWalk.size || activeWalk.size <= 1) {
            return TwinAffinityResult(0f, Float.MAX_VALUE, genIndex, emptyList())
        }

        val n = activeWalk.size
        var sumDistSq = 0.0
        val bridges = ArrayList<AnastomosisBridge>()

        for (t in 0 until n) {
            val dx = activeWalk.x[t] - historicalWalk.x[t]
            val dy = activeWalk.y[t] - historicalWalk.y[t]
            val dist = hypot(dx, dy)
            sumDistSq += (dx * dx + dy * dy)

            // Collect proximal bridge nodes (excluding the fixed origin endpoints)
            if (t in 1 until (n - 1) && dist < fusionThresholdDistance) {
                bridges.add(
                    AnastomosisBridge(
                        step = t,
                        ax = activeWalk.x[t],
                        ay = activeWalk.y[t],
                        bx = historicalWalk.x[t],
                        by = historicalWalk.y[t],
                        dist = dist
                    )
                )
            }
        }

        val rmsd = sqrt(sumDistSq / n).toFloat()
        val similarity = (exp(-rmsd * 0.35f) * 100f).coerceIn(0f, 100f)

        return TwinAffinityResult(
            similarityPercentage = similarity,
            rmsd = rmsd,
            matchedGeneration = genIndex,
            fusedBridges = bridges
        )
    }
}

// ============================================================================
// 3. CURVE FITTING & COLOR RESOLUTION
// ============================================================================

object CanvasTrajectoryRenderer {

    /**
     * Converts a discrete point sequence into a smooth Catmull-Rom cubic Bezier path.
     */
    fun buildSmoothSplinePath(
        path: Path,
        walk: TrajectoryPath,
        toScreen: (Float, Float) -> PointF,
        closeLoop: Boolean = true
    ) {
        path.reset()
        val count = walk.size
        if (count < 2) return

        val p0 = toScreen(walk.x[0], walk.y[0])
        path.moveTo(p0.x, p0.y)

        for (i in 0 until count - 1) {
            val iPrev = max(0, i - 1)
            val iCurr = i
            val iNext = i + 1
            val iAfter = min(count - 1, i + 2)

            val pPrev = toScreen(walk.x[iPrev], walk.y[iPrev])
            val pCurr = toScreen(walk.x[iCurr], walk.y[iCurr])
            val pNext = toScreen(walk.x[iNext], walk.y[iNext])
            val pAfter = toScreen(walk.x[iAfter], walk.y[iAfter])

            val cp1x = pCurr.x + (pNext.x - pPrev.x) / 6f
            val cp1y = pCurr.y + (pNext.y - pPrev.y) / 6f
            val cp2x = pNext.x - (pAfter.x - pCurr.x) / 6f
            val cp2y = pNext.y - (pAfter.y - pCurr.y) / 6f

            path.cubicTo(cp1x, cp1y, cp2x, cp2y, pNext.x, pNext.y)
        }

        if (closeLoop) {
            path.close()
        }
    }

    /**
     * Resolves seasonal strata aging with zoom-dependent cosmic mineral bloom.
     */
    fun getStrataColor(generationIndex: Int, zoomLevel: Float): Int {
        val zFactor = ((zoomLevel - 1f) / 7f).coerceIn(0f, 1f)

        // Earthy seasonal aging palette
        val earthColors = intArrayOf(
            0xD165A30D.toInt(), // k-1: Lush Summer Lichen
            0xB7B45309.toInt(), // k-2: Autumn Ochre Bark
            0x9978350F.toInt(), // k-3: Detritus Loam
            0x7A52525B.toInt()  // k-4: Weathered Granite Slate
        )

        // Deep cosmic minerals unlocked as camera zooms in
        val cosmicColors = intArrayOf(
            0xF206B6D4.toInt(), // Cyan
            0xE5A855F7.toInt(), // Amethyst
            0xE0F43F5E.toInt(), // Rose Nebula
            0xD910B981.toInt()  // Jade
        )

        val baseColor = if (generationIndex < earthColors.size) {
            earthColors[generationIndex]
        } else {
            // Fossilized ancient strata floor (minimum alpha floor preserved)
            0x384B5563.toInt()
        }

        val cosmicTarget = cosmicColors[generationIndex % cosmicColors.size]
        return blendArgb(baseColor, cosmicTarget, zFactor)
    }

    private fun blendArgb(c1: Int, c2: Int, ratio: Float): Int {
        val inv = 1f - ratio
        val a = (((c1 ushr 24) and 0xFF) * inv + ((c2 ushr 24) and 0xFF) * ratio).toInt().coerceIn(0, 255)
        val r = (((c1 ushr 16) and 0xFF) * inv + ((c2 ushr 16) and 0xFF) * ratio).toInt().coerceIn(0, 255)
        val g = (((c1 ushr 8) and 0xFF) * inv + ((c2 ushr 8) and 0xFF) * ratio).toInt().coerceIn(0, 255)
        val b = ((c1 and 0xFF) * inv + (c2 and 0xFF) * ratio).toInt().coerceIn(0, 255)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }
}
