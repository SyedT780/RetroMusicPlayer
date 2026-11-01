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

package code.name.monkey.retromusic.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ShuffleHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRun(run: ShuffleRunEntity): Long

    @Query("SELECT * FROM shuffle_runs ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentRuns(limit: Int): List<ShuffleRunEntity>

    @Query("SELECT * FROM shuffle_runs ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestRun(): ShuffleRunEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun markTrackPlayed(track: PlayedTrackEntity)

    @Query("SELECT trackId FROM played_tracks_history")
    suspend fun getAllPlayedTrackIds(): List<String>

    @Query("DELETE FROM played_tracks_history")
    suspend fun clearPlayedHistory()

    @Query("SELECT COUNT(*) FROM played_tracks_history")
    suspend fun getPlayedCount(): Int
}
