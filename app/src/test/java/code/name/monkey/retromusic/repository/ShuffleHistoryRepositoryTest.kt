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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShuffleHistoryRepositoryTest {

    private val testDispatcher = Dispatchers.Unconfined
    private val testScope = CoroutineScope(testDispatcher)

    private class FakeShuffleHistoryDao : ShuffleHistoryDao {
        val runs = mutableListOf<ShuffleRunEntity>()
        val playedTracks = mutableListOf<PlayedTrackEntity>()

        override suspend fun insertRun(run: ShuffleRunEntity): Long {
            runs.add(run)
            return runs.size.toLong()
        }

        override suspend fun getRecentRuns(limit: Int): List<ShuffleRunEntity> {
            return runs.takeLast(limit).reversed()
        }

        override suspend fun getLatestRun(): ShuffleRunEntity? {
            return runs.lastOrNull()
        }

        override suspend fun markTrackPlayed(track: PlayedTrackEntity) {
            playedTracks.removeAll { it.trackId == track.trackId }
            playedTracks.add(track)
        }

        override suspend fun getAllPlayedTrackIds(): List<String> {
            return playedTracks.map { it.trackId }
        }

        override suspend fun clearPlayedHistory() {
            playedTracks.clear()
        }

        override suspend fun getPlayedCount(): Int {
            return playedTracks.size
        }
    }

    @Test
    fun testFinalizeAndSaveSession() = runBlocking {
        val dao = FakeShuffleHistoryDao()
        val repo = ShuffleHistoryRepository(dao, testScope, testDispatcher)

        val playedTracks = listOf(0, 1, 2, 3, 4)
        repo.finalizeAndSaveSession(
            playedTracks = playedTracks,
            totalN = 20,
            engineMode = "Enhanced Strict-Tail",
            slotMatches = 1,
            repeatedTransitions = 0
        )

        assertEquals("Cached previous finalized run must match saved tracks", playedTracks, repo.cachedPreviousFinalizedRun)
        assertEquals(1, dao.runs.size)
        assertEquals(20, dao.runs.first().totalDomainN)
    }

    @Test
    fun testMarkTrackConsumedAndGetPlayedTrackIds() = runBlocking {
        val dao = FakeShuffleHistoryDao()
        val repo = ShuffleHistoryRepository(dao, testScope, testDispatcher)

        repo.markTrackConsumed("track_101", 1)
        repo.markTrackConsumed("track_102", 2)

        val ids = repo.getPlayedTrackIds()
        assertTrue("Played track IDs should contain track_101", ids.contains("track_101"))
        assertTrue("Played track IDs should contain track_102", ids.contains("track_102"))

        repo.resetMacroCycle()
        assertTrue("Played track IDs should be empty after resetMacroCycle", repo.getPlayedTrackIds().isEmpty())
    }
}
