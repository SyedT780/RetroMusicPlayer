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
import java.util.ArrayDeque
import kotlin.random.Random

/**
 * Hybrid Anti-Clustering Shuffle Engine
 *
 * Layer 1: Sub-Linear Permutation Streamer based on a 4-round Feistel cipher with cycle-walking.
 * Layer 2: Perceptual Smoothing Buffer evaluating candidates against an LRU Cooldown queue.
 */
object EnhancedShuffleEngine {

    data class AttributeDescriptor(val artistId: Long, val albumId: Long)

    /**
     * Constant-Memory Pseudorandom Permutation (PRP) generator.
     * Computes a bijective permuted index j in [0, n - 1] given stream index i, total size n, and seed.
     */
    fun generatePRPIndex(i: Int, n: Int, seed: Long): Int {
        if (n <= 1) return 0
        var bits = 0
        var temp = n - 1
        while (temp > 0) {
            bits++
            temp = temp shr 1
        }
        val k = (bits + 1) / 2
        val mask = (1L shl k) - 1L

        val k0 = (seed xor 0x9E3779B9L) and 0xFFFFFFFFL
        val k1 = (seed xor 0x63C63CD9L) and 0xFFFFFFFFL
        val k2 = (seed xor 0x1A85EC53L) and 0xFFFFFFFFL
        val k3 = (seed xor 0x7149F0A9L) and 0xFFFFFFFFL
        val keys = longArrayOf(k0, k1, k2, k3)

        var v = i.toLong()
        while (true) {
            var l = (v ushr k) and mask
            var r = v and mask

            for (rIdx in 0..3) {
                var f = ((r xor keys[rIdx]) * 0x45D9F3BL) and 0xFFFFFFFFL
                f = ((f ushr 16) xor f) and 0xFFFFFFFFL
                f = (f * 0x45D9F3BL) and 0xFFFFFFFFL
                f = ((f ushr 16) xor f) and mask

                val tempVal = l xor f
                l = r
                r = tempVal
            }

            val y = (l shl k) or r
            if (y < n) {
                return y.toInt()
            }
            v = y
        }
    }

    /**
     * Evaluates heuristic spacing penalty for a candidate track against recent playback timeline.
     */
    fun calculatePenalty(
        candidate: Song,
        cooldown: List<AttributeDescriptor>,
        cooldownDepth: Int,
        weightArtist: Double = 10.0,
        weightAlbum: Double = 5.0,
        seed: Long,
        streamIndex: Int,
        slotIndex: Int
    ): Double {
        var artistRecency = 0
        for (p in cooldown.indices) {
            if (cooldown[p].artistId == candidate.artistId) {
                artistRecency = cooldownDepth - p
                break
            }
        }

        var albumRecency = 0
        for (p in cooldown.indices) {
            if (cooldown[p].albumId == candidate.albumId) {
                albumRecency = cooldownDepth - p
                break
            }
        }

        val rawVal = (seed + streamIndex + slotIndex) * 1103515245L + 12345L
        val modVal = ((rawVal % 100) + 100) % 100
        val eta = modVal / 200.0

        return weightArtist * artistRecency + weightAlbum * albumRecency + eta
    }

    /**
     * Reorders [listToShuffle] in-place using the Hybrid Anti-Clustering algorithm.
     *
     * @param listToShuffle List of songs to shuffle.
     * @param current Index of the currently playing/starting song, or -1 if no fixed start song.
     * @param customSeed Optional seed for testing or deterministic session setup.
     */
    fun shuffle(
        listToShuffle: MutableList<Song>,
        current: Int = -1,
        customSeed: Long? = null
    ) {
        val n = listToShuffle.size
        if (n <= 1) return

        val distinctArtists = listToShuffle.map { it.artistId }.distinct().size
        val calculatedW = maxOf(6, minOf(32, n / 5))
        val w = minOf(n, calculatedW)
        val c = maxOf(1, minOf(12, maxOf(1, distinctArtists - 1)))

        val seed = customSeed ?: Random.nextLong()
        val result = ArrayList<Song>(n)
        val cooldown = ArrayDeque<AttributeDescriptor>()
        var streamPointer = 0

        val validCurrent = current in 0 until n
        if (validCurrent) {
            val startSong = listToShuffle[current]
            result.add(startSong)
            cooldown.addFirst(AttributeDescriptor(startSong.artistId, startSong.albumId))
            if (cooldown.size > c) {
                cooldown.removeLast()
            }
        }

        val buffer = ArrayList<Song>()

        // Prime lookahead buffer
        while (buffer.size < w && streamPointer < n) {
            val j = generatePRPIndex(streamPointer, n, seed)
            streamPointer++
            if (validCurrent && j == current) {
                continue
            }
            buffer.add(listToShuffle[j])
        }

        // Candidate selection loop
        while (buffer.isNotEmpty()) {
            var bestCandidateIndex = 0
            var minPenalty = Double.MAX_VALUE

            for (slot in buffer.indices) {
                val candidate = buffer[slot]
                val penalty = calculatePenalty(
                    candidate = candidate,
                    cooldown = cooldown.toList(),
                    cooldownDepth = c,
                    weightArtist = 10.0,
                    weightAlbum = 5.0,
                    seed = seed,
                    streamIndex = streamPointer,
                    slotIndex = slot
                )
                if (penalty < minPenalty) {
                    minPenalty = penalty
                    bestCandidateIndex = slot
                }
            }

            val selectedSong = buffer.removeAt(bestCandidateIndex)
            result.add(selectedSong)

            cooldown.addFirst(AttributeDescriptor(selectedSong.artistId, selectedSong.albumId))
            if (cooldown.size > c) {
                cooldown.removeLast()
            }

            // Replenish lookahead buffer
            while (buffer.size < w && streamPointer < n) {
                val j = generatePRPIndex(streamPointer, n, seed)
                streamPointer++
                if (validCurrent && j == current) {
                    continue
                }
                buffer.add(listToShuffle[j])
            }
        }

        listToShuffle.clear()
        listToShuffle.addAll(result)
    }
}
