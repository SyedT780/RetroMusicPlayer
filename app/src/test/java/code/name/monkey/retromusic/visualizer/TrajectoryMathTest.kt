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
    fun testTrajectoryPathBoundingBox() {
        val n = 20
        val perm = IntArray(n) { it }
        val random = Random(1234)
        perm.shuffle(random)

        val path = TrajectoryMath.computeWalk(perm)

        assertEquals(n + 1, path.size)
        assertEquals(0f, path.x[0], 1e-5f)
        assertEquals(0f, path.y[0], 1e-5f)

        for (t in 0..n) {
            val xt = path.x[t]
            val yt = path.y[t]
            assertTrue("x[$t] = $xt should be >= minX ${path.minX}", xt >= path.minX - 1e-5f)
            assertTrue("x[$t] = $xt should be <= maxX ${path.maxX}", xt <= path.maxX + 1e-5f)
            assertTrue("y[$t] = $yt should be >= minY ${path.minY}", yt >= path.minY - 1e-5f)
            assertTrue("y[$t] = $yt should be <= maxY ${path.maxY}", yt <= path.maxY + 1e-5f)
        }
    }
}
