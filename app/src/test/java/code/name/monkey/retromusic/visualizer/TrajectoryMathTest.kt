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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class TrajectoryMathTest {

    private val floatEpsilon = 1e-3f

    @Test
    fun testRootsOfUnityConvergenceToOrigin() {
        val testSizes = listOf(2, 5, 10, 50, 100, 500)

        for (n in testSizes) {
            // Test identity permutation
            val identityPerm = IntArray(n) { it }
            val pathIdentity = TrajectoryMath.computeWalk(identityPerm)
            assertEquals("Identity perm (N=$n) x[N] must return to origin 0", 0f, pathIdentity.x[n], floatEpsilon)
            assertEquals("Identity perm (N=$n) y[N] must return to origin 0", 0f, pathIdentity.y[n], floatEpsilon)
            assertTrue("maxRadius should be >= 1.0f", pathIdentity.maxRadius >= 1.0f)

            // Test reverse permutation
            val reversePerm = IntArray(n) { n - 1 - it }
            val pathReverse = TrajectoryMath.computeWalk(reversePerm)
            assertEquals("Reverse perm (N=$n) x[N] must return to origin 0", 0f, pathReverse.x[n], floatEpsilon)
            assertEquals("Reverse perm (N=$n) y[N] must return to origin 0", 0f, pathReverse.y[n], floatEpsilon)

            // Test randomized bijective permutation
            val random = Random(42 + n)
            val randomPerm = identityPerm.clone()
            randomPerm.shuffle(random)
            val pathRandom = TrajectoryMath.computeWalk(randomPerm)
            assertEquals("Random perm (N=$n) x[N] must return to origin 0", 0f, pathRandom.x[n], floatEpsilon)
            assertEquals("Random perm (N=$n) y[N] must return to origin 0", 0f, pathRandom.y[n], floatEpsilon)
        }
    }

    @Test
    fun testMicroBranchletsGeneration() {
        val n = 15
        val perm = IntArray(n) { it }
        val walk = TrajectoryMath.computeWalk(perm)

        assertEquals(n + 1, walk.size)
        assertFalse("Branch offsets should be generated for N >= 3", walk.branchOffsets.isEmpty())

        for (branch in walk.branchOffsets) {
            assertTrue("Branch source index should be within bounds", branch.srcIdx in 0 until n)
            assertTrue("Depth seed should be in 0.0..1.0", branch.depthSeed in 0.0f..1.0f)
        }
    }

    @Test
    fun testTwinAffinityCalculation() {
        val n = 20
        val perm = IntArray(n) { it }
        val walk1 = TrajectoryMath.computeWalk(perm)
        val walk2 = TrajectoryMath.computeWalk(perm)

        // Identical trajectories must yield 100% similarity and 0 RMSD
        val affinityIdentical = TrajectoryMath.computeTwinAffinity(walk1, walk2, genIndex = 1)
        assertEquals("Identical walks should yield 100% similarity", 100.0f, affinityIdentical.similarityPercentage, floatEpsilon)
        assertEquals("Identical walks should yield 0.0 RMSD", 0.0f, affinityIdentical.rmsd, floatEpsilon)

        // Slightly different/shuffled trajectories
        val permShuffled = perm.clone()
        permShuffled.shuffle(Random(999))
        val walkShuffled = TrajectoryMath.computeWalk(permShuffled)

        val affinityShuffled = TrajectoryMath.computeTwinAffinity(walk1, walkShuffled, genIndex = 1)
        assertTrue("Shuffled walks should have similarity <= 100%", affinityShuffled.similarityPercentage <= 100.0f)
        assertTrue("Shuffled walks should have RMSD > 0", affinityShuffled.rmsd > 0f)
    }

    @Test
    fun testStrataColorResolution() {
        // Base strata colors
        val colorK1 = CanvasTrajectoryRenderer.getStrataColor(0, 1.0f)
        val colorK2 = CanvasTrajectoryRenderer.getStrataColor(1, 1.0f)
        val colorFossil = CanvasTrajectoryRenderer.getStrataColor(10, 1.0f)

        assertTrue("Color for k-1 should be valid ARGB", colorK1 != 0)
        assertTrue("Color for k-2 should be valid ARGB", colorK2 != 0)
        assertTrue("Color for fossilized strata should be valid ARGB", colorFossil != 0)

        // Cosmic bloom shift on zoom
        val colorZoomed = CanvasTrajectoryRenderer.getStrataColor(0, 8.0f)
        assertTrue("Zoomed color should differ due to cosmic mineral bloom", colorK1 != colorZoomed)
    }
}
