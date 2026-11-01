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

package code.name.monkey.retromusic.service

import code.name.monkey.retromusic.extensions.computeInvariantRanks
import code.name.monkey.retromusic.helper.EnhancedStrictTailShuffleEngine
import code.name.monkey.retromusic.helper.EnhancedStrictTailShuffleEngine.Companion.toShuffleTracks
import code.name.monkey.retromusic.model.Song
import code.name.monkey.retromusic.repository.ShuffleHistoryRepository
import code.name.monkey.retromusic.visualizer.DualLayerFrame
import code.name.monkey.retromusic.visualizer.DualLayerShuffleVisualizer
import code.name.monkey.retromusic.visualizer.DualLayerShuffleVisualizer.Companion.toTrackItems
import kotlinx.coroutines.runBlocking

class MusicPlaybackController(
    private val historyRepo: ShuffleHistoryRepository,
    private val shuffleEngine: EnhancedStrictTailShuffleEngine = EnhancedStrictTailShuffleEngine(),
    private val dualLayerEngine: DualLayerShuffleVisualizer = DualLayerShuffleVisualizer()
) {
    private var currentQueue: MutableList<Song> = ArrayList()
    private var currentCursor: Int = 0

    /**
     * Called when the user triggers a new shuffle.
     */
    fun onUserTriggeredShuffle(fullLibrary: List<Song>, startingIndex: Int = 0): List<Song> {
        val n = fullLibrary.size
        if (n == 0) return emptyList()

        val rankMap = fullLibrary.computeInvariantRanks()

        // Step 1: Truncate current session to played tracks (0..m) and save to Room DB
        if (currentQueue.isNotEmpty()) {
            val finalizedLength = (currentCursor + 1).coerceAtMost(currentQueue.size)
            val playedIds = currentQueue.subList(0, finalizedLength).map { rankMap[it.id] ?: 0 }

            val bigrams = HashSet<Pair<Int, Int>>()
            for (i in 0 until playedIds.size - 1) {
                bigrams.add(Pair(playedIds[i], playedIds[i + 1]))
            }

            val prevRun = historyRepo.cachedPreviousFinalizedRun
            var slotMatches = 0
            val minLen = minOf(playedIds.size, prevRun.size)
            for (i in 0 until minLen) {
                if (playedIds[i] == prevRun[i]) slotMatches++
            }

            historyRepo.finalizeAndSaveSession(
                playedTracks = playedIds,
                totalN = n,
                engineMode = "Enhanced Strict-Tail",
                slotMatches = slotMatches,
                repeatedTransitions = 0
            )
        }

        // Step 2: Retrieve persistent played history
        val persistentPlayed = runBlocking { historyRepo.getPlayedTrackIds() }

        val priorRun = historyRepo.cachedPreviousFinalizedRun
        val priorBigrams = HashSet<Pair<Int, Int>>()
        for (i in 0 until priorRun.size - 1) {
            priorBigrams.add(Pair(priorRun[i], priorRun[i + 1]))
        }

        // Step 3: Generate strict two-tier queue
        val shuffleTracks = fullLibrary.toShuffleTracks()
        val generatedTracks = shuffleEngine.generateQueue(
            domain = shuffleTracks,
            currentTrackIndex = startingIndex,
            persistentPlayedHistory = persistentPlayed,
            previousRunBigrams = priorBigrams
        )

        val idToSongMap = fullLibrary.associateBy { it.id.toString() }
        val newShuffled = generatedTracks.mapNotNull { idToSongMap[it.id] }

        currentQueue = newShuffled.toMutableList()
        currentCursor = startingIndex

        // Step 4: Freeze immutable baseline trajectory
        dualLayerEngine.onNewShuffleTriggered(newShuffled.toTrackItems(), startingIndex)

        return newShuffled
    }

    /**
     * Called when track playback passes consumption threshold.
     */
    fun onTrackConsumed(song: Song, fullLibrary: List<Song> = currentQueue) {
        val rankMap = fullLibrary.computeInvariantRanks()
        val invId = rankMap[song.id] ?: 0
        historyRepo.markTrackConsumed(song.id.toString(), invId)
    }

    fun onCursorAdvanced(newIndex: Int) {
        this.currentCursor = newIndex
        dualLayerEngine.onPlaybackCursorChanged(newIndex)
    }

    fun getCurrentVisualizerFrame(queue: List<Song> = currentQueue): DualLayerFrame {
        return dualLayerEngine.computeLiveFrame(queue.toTrackItems())
    }
}
