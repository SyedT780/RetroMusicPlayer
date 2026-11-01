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

import code.name.monkey.retromusic.extensions.computeInvariantRanks
import code.name.monkey.retromusic.model.Song
import java.util.ArrayDeque
import kotlin.random.Random

data class ShuffleTrack(
    val id: String,
    val invariantId: Int,
    val artistId: Long,
    val albumId: Long,
    val title: String
)

class EnhancedStrictTailShuffleEngine(
    private val cooldownDepth: Int = 8,
    private val windowCapacity: Int = 16
) {
    /**
     * Generates a strict two-tier queue without mutating persistent history.
     */
    fun generateQueue(
        domain: List<ShuffleTrack>,
        currentTrackIndex: Int,
        persistentPlayedHistory: Set<String>,
        previousRunBigrams: Set<Pair<Int, Int>>,
        customSeed: Long? = null
    ): List<ShuffleTrack> {
        val totalN = domain.size
        if (totalN == 0) return emptyList()

        val activeHistory = if (domain.isNotEmpty() && domain.all { it.id in persistentPlayedHistory }) {
            emptySet() // Macro-cycle boundary: all played -> reset
        } else {
            persistentPlayedHistory
        }

        val unplayedPool = domain.filter { it.id !in activeHistory }.toMutableList()
        val playedPool = domain.filter { it.id in activeHistory }.toMutableList()

        val resultQueue = ArrayList<ShuffleTrack>(totalN)
        val cooldown = ArrayDeque<ShuffleTrack>(cooldownDepth)
        val sessionRandom = if (customSeed != null) Random(customSeed) else Random(System.nanoTime())

        // Pinned track handling
        if (currentTrackIndex in domain.indices) {
            val pinned = domain[currentTrackIndex]
            resultQueue.add(pinned)
            cooldown.addLast(pinned)
            unplayedPool.removeAll { it.id == pinned.id }
            playedPool.removeAll { it.id == pinned.id }
        }

        unplayedPool.shuffle(sessionRandom)
        playedPool.shuffle(sessionRandom)

        val lookahead = ArrayList<ShuffleTrack>(windowCapacity)

        // Phase 1: Unplayed tracks (0 .. U-1)
        dispatchPool(unplayedPool, lookahead, resultQueue, cooldown, previousRunBigrams, sessionRandom)

        // Strict boundary: clear buffer to avoid tier leakage
        lookahead.clear()

        // Phase 2: Quarantined played tracks (U .. N-1)
        dispatchPool(playedPool, lookahead, resultQueue, cooldown, previousRunBigrams, sessionRandom)

        return resultQueue
    }

    private fun dispatchPool(
        pool: MutableList<ShuffleTrack>,
        lookahead: ArrayList<ShuffleTrack>,
        result: ArrayList<ShuffleTrack>,
        cooldown: ArrayDeque<ShuffleTrack>,
        previousRunBigrams: Set<Pair<Int, Int>>,
        random: Random
    ) {
        while (pool.isNotEmpty() || lookahead.isNotEmpty()) {
            while (lookahead.size < windowCapacity && pool.isNotEmpty()) {
                lookahead.add(pool.removeAt(0))
            }

            var bestCandidate: ShuffleTrack? = null
            var lowestPenalty = Double.MAX_VALUE
            val lastPlacedId = result.lastOrNull()?.invariantId

            for (candidate in lookahead) {
                val rArtist = computeRecency(candidate.artistId, cooldown.map { it.artistId })
                val rAlbum = computeRecency(candidate.albumId, cooldown.map { it.albumId })

                // Fix 1: Non-deterministic tie-breaker jitter
                val dynamicJitter = random.nextDouble(0.0, 0.495)

                // Fix 2: Anti-Bigram Repeat Penalty
                val isRepeatedBigram = lastPlacedId != null &&
                        Pair(lastPlacedId, candidate.invariantId) in previousRunBigrams
                val bigramPenalty = if (isRepeatedBigram) 25.0 else 0.0

                val penalty = (10.0 * rArtist) + (1.0 * rAlbum) + bigramPenalty + dynamicJitter

                if (penalty < lowestPenalty) {
                    lowestPenalty = penalty
                    bestCandidate = candidate
                }
            }

            val selected = bestCandidate ?: lookahead.first()
            lookahead.remove(selected)
            result.add(selected)

            if (cooldown.size >= cooldownDepth) cooldown.removeFirst()
            cooldown.addLast(selected)
        }
    }

    private fun computeRecency(id: Long, history: List<Long>): Double {
        val idx = history.lastIndexOf(id)
        return if (idx == -1) 0.0 else (cooldownDepth - (history.size - 1 - idx)).coerceAtLeast(0).toDouble()
    }

    companion object {
        fun Song.toShuffleTrack(invariantId: Int = 0): ShuffleTrack {
            return ShuffleTrack(
                id = id.toString(),
                invariantId = invariantId,
                artistId = artistId,
                albumId = albumId,
                title = title
            )
        }

        fun List<Song>.toShuffleTracks(): List<ShuffleTrack> {
            if (isEmpty()) return emptyList()
            val rankMap = computeInvariantRanks()
            return map { song ->
                song.toShuffleTrack(rankMap[song.id] ?: 0)
            }
        }
    }
}
