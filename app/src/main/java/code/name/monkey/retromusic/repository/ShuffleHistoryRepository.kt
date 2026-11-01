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

package code.name.monkey.retromusic.repository

import code.name.monkey.retromusic.db.PlayedTrackEntity
import code.name.monkey.retromusic.db.ShuffleHistoryDao
import code.name.monkey.retromusic.db.ShuffleRunEntity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ShuffleHistoryRepository(
    private val dao: ShuffleHistoryDao,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val gson = Gson()
    private val intListType = object : TypeToken<List<Int>>() {}.type

    // In-memory cache for 60 FPS visualizer queries
    var cachedPreviousFinalizedRun: List<Int> = emptyList()
        private set

    init {
        // Hydrate latest run from disk on app cold start
        scope.launch(ioDispatcher) {
            val latest = dao.getLatestRun()
            if (latest != null) {
                cachedPreviousFinalizedRun = try {
                    gson.fromJson<List<Int>>(latest.playedTracksJson, intListType) ?: emptyList()
                } catch (e: Throwable) {
                    emptyList()
                }
            }
        }
    }

    suspend fun getPlayedTrackIds(): Set<String> = withContext(ioDispatcher) {
        try {
            dao.getAllPlayedTrackIds().toSet()
        } catch (e: Throwable) {
            emptySet()
        }
    }

    fun markTrackConsumed(trackId: String, invariantId: Int) {
        scope.launch(ioDispatcher) {
            try {
                dao.markTrackPlayed(PlayedTrackEntity(trackId, invariantId))
            } catch (ignored: Throwable) {
            }
        }
    }

    fun resetMacroCycle() {
        scope.launch(ioDispatcher) {
            try {
                dao.clearPlayedHistory()
            } catch (ignored: Throwable) {
            }
        }
    }

    fun finalizeAndSaveSession(
        playedTracks: List<Int>,
        totalN: Int,
        engineMode: String,
        slotMatches: Int,
        repeatedTransitions: Int
    ) {
        cachedPreviousFinalizedRun = ArrayList(playedTracks)
        val json = gson.toJson(playedTracks)
        val repetitiveness = if (totalN > 0) {
            ((0.5f * slotMatches / totalN) + (0.5f * repeatedTransitions / maxOf(1, totalN - 1))) * 100f
        } else 0f

        scope.launch(ioDispatcher) {
            try {
                dao.insertRun(
                    ShuffleRunEntity(
                        totalDomainN = totalN,
                        engineMode = engineMode,
                        playedTracksJson = json,
                        slotMatches = slotMatches,
                        repeatedTransitions = repeatedTransitions,
                        repetitivenessScore = repetitiveness
                    )
                )
            } catch (ignored: Throwable) {
            }
        }
    }
}
