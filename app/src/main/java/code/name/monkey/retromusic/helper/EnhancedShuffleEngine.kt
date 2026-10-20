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
 * Two-Tier Persistent Anti-Clustering Shuffle Engine
 *
 * Separates persistent macro-cycle history from session-specific randomization.
 * Dynamically partitions the shuffle domain into Tier 1 (Unplayed tracks) at the front
 * of the virtual stream, and Tier 2 (Played tracks) pushed to the extreme tail.
 */
object EnhancedShuffleEngine {

    data class AttributeDescriptor(val artistId: Long, val albumId: Long = 0L)

    // Persistent Macro-Cycle State (Preserved across reshuffles)
    private var bitset: LongArray = LongArray(0)
    private var domainN: Int = 0
    private var mPlayed: Int = 0
    private var uUnplayed: Int = 0

    // Ephemeral Session State
    private var sUnplayed: Long = 0L
    private var sPlayed: Long = 0L
    private var streamPointer: Int = 0
    private val cooldown = ArrayDeque<AttributeDescriptor>()

    @Synchronized
    fun getM(): Int = mPlayed

    @Synchronized
    fun getU(): Int = uUnplayed

    @Synchronized
    fun getDomainSize(): Int = domainN

    @Synchronized
    fun getBitset(): LongArray = bitset.clone()

    @Synchronized
    fun getCooldown(): List<AttributeDescriptor> = cooldown.toList()

    /**
     * Resets persistent macro-cycle state manually (MacroCycleReset / Procedure C).
     */
    @Synchronized
    fun macroCycleReset(n: Int = domainN) {
        domainN = n
        val wordCount = if (n > 0) (n + 63) / 64 else 0
        bitset = LongArray(wordCount)
        mPlayed = 0
        uUnplayed = n
        // Critical rule: Do not clear cooldown (preserving last C tracks prevents boundary collisions)
    }

    /**
     * Constant-Memory Pseudorandom Permutation (PRP) generator (Sub-Algorithm 1).
     * Computes a bijective permuted index in [0, d - 1] given counter x, domain d, and seed.
     */
    fun generatePRPIndex(x: Int, d: Int, seed: Long): Int {
        if (d <= 1) return 0
        var bits = 0
        var temp = d - 1
        while (temp > 0) {
            bits++
            temp = temp shr 1
        }
        val k = maxOf(1, (bits + 1) / 2)
        val mask = (1L shl k) - 1L

        val k0 = (seed xor 0x9E3779B9L) and 0xFFFFFFFFL
        val k1 = (seed xor 0x63C63CD9L) and 0xFFFFFFFFL
        val k2 = (seed xor 0x1A85EC53L) and 0xFFFFFFFFL
        val k3 = (seed xor 0x7149F0A9L) and 0xFFFFFFFFL
        val roundKeys = longArrayOf(k0, k1, k2, k3)

        var v = x.toLong()
        while (true) {
            var l = (v ushr k) and mask
            var r = v and mask

            for (rIdx in 0..3) {
                var f = ((r xor roundKeys[rIdx]) * 0x45D9F3BL) and 0xFFFFFFFFL
                f = ((f ushr 16) xor f) and 0xFFFFFFFFL
                f = (f * 0x45D9F3BL) and 0xFFFFFFFFL
                f = ((f ushr 16) xor f) and mask

                val tempVal = l xor f
                l = r
                r = tempVal
            }

            val y = (l shl k) or r
            if (y < d) {
                return y.toInt()
            }
            v = y
        }
    }

    /**
     * Succinct Rank-Select Addressing for unplayed tracks (Sub-Algorithm 2: SelectZero).
     * Scans bitset word by word, inverting words (~word) to count zero-bits via hardware popcount
     * until cumulative count matches rank + 1, returning physical track index.
     */
    fun selectZero(rank: Int, bitset: LongArray, n: Int): Int {
        var accumulatedZeros = 0
        val numWords = bitset.size
        for (w in 0 until numWords) {
            val word = bitset[w]
            val invertedWord = word.inv()
            val startBit = w * 64
            val remainingBits = n - startBit
            if (remainingBits <= 0) break

            val validMask = if (remainingBits >= 64) -1L else (1L shl remainingBits) - 1L
            val maskedInverted = invertedWord and validMask
            val zeroCountInWord = java.lang.Long.bitCount(maskedInverted)

            if (accumulatedZeros + zeroCountInWord > rank) {
                val targetInWord = rank - accumulatedZeros
                var currentZero = 0
                val maxBitInWord = minOf(63, remainingBits - 1)
                for (b in 0..maxBitInWord) {
                    if ((word and (1L shl b)) == 0L) {
                        if (currentZero == targetInWord) {
                            return startBit + b
                        }
                        currentZero++
                    }
                }
            }
            accumulatedZeros += zeroCountInWord
        }
        return 0
    }

    /**
     * Succinct Rank-Select Addressing for played tracks (Sub-Algorithm 2: SelectOne).
     * Scans bitset word by word using hardware popcount until cumulative count matches rank + 1,
     * returning physical track index.
     */
    fun selectOne(rank: Int, bitset: LongArray, n: Int): Int {
        var accumulatedOnes = 0
        val numWords = bitset.size
        for (w in 0 until numWords) {
            val word = bitset[w]
            val startBit = w * 64
            val remainingBits = n - startBit
            if (remainingBits <= 0) break

            val validMask = if (remainingBits >= 64) -1L else (1L shl remainingBits) - 1L
            val maskedWord = word and validMask
            val oneCountInWord = java.lang.Long.bitCount(maskedWord)

            if (accumulatedOnes + oneCountInWord > rank) {
                val targetInWord = rank - accumulatedOnes
                var currentOne = 0
                val maxBitInWord = minOf(63, remainingBits - 1)
                for (b in 0..maxBitInWord) {
                    if ((word and (1L shl b)) != 0L) {
                        if (currentOne == targetInWord) {
                            return startBit + b
                        }
                        currentOne++
                    }
                }
            }
            accumulatedOnes += oneCountInWord
        }
        return 0
    }

    /**
     * Virtual Partitioned Stream Routing (Sub-Algorithm 3: ResolveTrackIndex).
     * Maps linear stream counter i to physical track index across Tier 1 (unplayed) and Tier 2 (played).
     */
    fun resolveTrackIndex(
        i: Int,
        n: Int,
        m: Int,
        u: Int,
        sUnplayed: Long,
        sPlayed: Long,
        bitset: LongArray
    ): Int {
        return if (i < u) {
            // Tier 1: Draw from unplayed tracks
            val rank = generatePRPIndex(i, u, sUnplayed)
            selectZero(rank, bitset, n)
        } else {
            // Tier 2: Draw from played tracks pushed to extreme tail
            val subI = i - u
            val rank = generatePRPIndex(subI, m, sPlayed)
            selectOne(rank, bitset, n)
        }
    }

    /**
     * Anti-Clustering Penalty Heuristic (Sub-Algorithm 4: CalculatePenalty).
     * Evaluates recency penalty against recent playback timeline in cooldown queue.
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
            if (cooldown[p].albumId != 0L && cooldown[p].albumId == candidate.albumId) {
                albumRecency = cooldownDepth - p
                break
            }
        }

        val rawVal = (seed + streamIndex + slotIndex) * 1103515245L + 12345L
        val modVal = Math.floorMod(rawVal, 100L)
        val eta = modVal / 200.0

        return weightArtist * artistRecency + weightAlbum * albumRecency + eta
    }

    /**
     * Reorders [listToShuffle] in-place using the Two-Tier Persistent Anti-Clustering algorithm.
     *
     * @param listToShuffle List of songs to shuffle.
     * @param current Index of currently playing/starting song, or -1 if no fixed start song.
     * @param customSeed Optional seed for testing or deterministic session setup.
     */
    @Synchronized
    fun shuffle(
        listToShuffle: MutableList<Song>,
        current: Int = -1,
        customSeed: Long? = null
    ) {
        val n = listToShuffle.size
        if (n <= 1) return

        // Initialize or reset persistent macro-cycle state if domain size changed or all tracks played
        if (domainN != n || uUnplayed == 0) {
            macroCycleReset(n)
        }

        // Snapshot session macro-cycle partition counts and bitset for stream routing
        val sessionU = uUnplayed
        val sessionM = mPlayed
        val sessionBitset = bitset.clone()

        val distinctArtists = listToShuffle.map { it.artistId }.distinct().size
        val calculatedW = maxOf(6, minOf(32, n / 5))
        val w = minOf(n, calculatedW)
        val c = maxOf(1, minOf(12, maxOf(1, distinctArtists - 1)))

        // Reshuffle Trigger (Procedure A): Re-seed ephemeral session state
        sUnplayed = customSeed ?: Random.nextLong()
        sPlayed = customSeed?.let { it xor 0x5A5A5A5A5A5A5A5AL } ?: Random.nextLong()
        streamPointer = 0

        val result = ArrayList<Song>(n)
        val buffer = ArrayList<Int>() // holds candidate physical track indices
        val chosenSet = HashSet<Int>() // tracks already placed in result

        val validCurrent = current in 0 until n
        if (validCurrent) {
            val startSong = listToShuffle[current]
            result.add(startSong)
            chosenSet.add(current)
            commitTrackToBitset(current)
            updateCooldown(AttributeDescriptor(startSong.artistId, startSong.albumId), c)
        }

        // Prime Buffer (Procedure A)
        while (buffer.size < w && streamPointer < n) {
            val trackIdx = resolveTrackIndex(
                i = streamPointer,
                n = n,
                m = sessionM,
                u = sessionU,
                sUnplayed = sUnplayed,
                sPlayed = sPlayed,
                bitset = sessionBitset
            )
            streamPointer++
            if (chosenSet.contains(trackIdx)) {
                continue
            }
            if (!buffer.contains(trackIdx)) {
                buffer.add(trackIdx)
            }
        }

        // Candidate Selection & Dispatch Loop (Procedure B: OnNextTrack)
        while (buffer.isNotEmpty() || result.size < n) {
            if (buffer.isEmpty()) {
                // Replenish buffer if streamPointer still has elements
                while (buffer.size < w && streamPointer < n) {
                    val trackIdx = resolveTrackIndex(
                        i = streamPointer,
                        n = n,
                        m = sessionM,
                        u = sessionU,
                        sUnplayed = sUnplayed,
                        sPlayed = sPlayed,
                        bitset = sessionBitset
                    )
                    streamPointer++
                    if (chosenSet.contains(trackIdx)) {
                        continue
                    }
                    if (!buffer.contains(trackIdx)) {
                        buffer.add(trackIdx)
                    }
                }
                if (buffer.isEmpty()) {
                    // Fallback for any unchosen remaining indices
                    for (idx in 0 until n) {
                        if (!chosenSet.contains(idx)) {
                            buffer.add(idx)
                            break
                        }
                    }
                    if (buffer.isEmpty()) break
                }
            }

            var bestSlot = 0
            var minPenalty = Double.MAX_VALUE

            for (slot in buffer.indices) {
                val candidateIdx = buffer[slot]
                val candidateSong = listToShuffle[candidateIdx]
                val penalty = calculatePenalty(
                    candidate = candidateSong,
                    cooldown = cooldown.toList(),
                    cooldownDepth = c,
                    weightArtist = 10.0,
                    weightAlbum = 0.0,
                    seed = sUnplayed,
                    streamIndex = streamPointer,
                    slotIndex = slot
                )
                if (penalty < minPenalty) {
                    minPenalty = penalty
                    bestSlot = slot
                }
            }

            val chosenTrackIdx = buffer.removeAt(bestSlot)
            val chosenSong = listToShuffle[chosenTrackIdx]
            result.add(chosenSong)
            chosenSet.add(chosenTrackIdx)

            commitTrackToBitset(chosenTrackIdx)
            updateCooldown(AttributeDescriptor(chosenSong.artistId, chosenSong.albumId), c)

            // Buffer Replenishment (Procedure B)
            while (buffer.size < w && streamPointer < n) {
                val trackIdx = resolveTrackIndex(
                    i = streamPointer,
                    n = n,
                    m = sessionM,
                    u = sessionU,
                    sUnplayed = sUnplayed,
                    sPlayed = sPlayed,
                    bitset = sessionBitset
                )
                streamPointer++
                if (chosenSet.contains(trackIdx)) {
                    continue
                }
                if (!buffer.contains(trackIdx)) {
                    buffer.add(trackIdx)
                }
            }
        }

        listToShuffle.clear()
        listToShuffle.addAll(result)
    }

    private fun commitTrackToBitset(trackIdx: Int) {
        val wordIdx = trackIdx / 64
        val bitIdx = trackIdx % 64
        if (wordIdx in bitset.indices) {
            val mask = 1L shl bitIdx
            if ((bitset[wordIdx] and mask) == 0L) {
                bitset[wordIdx] = bitset[wordIdx] or mask
                mPlayed++
                uUnplayed = maxOf(0, domainN - mPlayed)
            }
        }
    }

    private fun updateCooldown(attr: AttributeDescriptor, cooldownDepth: Int) {
        cooldown.addFirst(attr)
        while (cooldown.size > cooldownDepth) {
            cooldown.removeLast()
        }
    }
}
