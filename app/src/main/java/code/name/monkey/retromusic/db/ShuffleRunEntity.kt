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

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shuffle_runs")
data class ShuffleRunEntity(
    @PrimaryKey(autoGenerate = true) val runId: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val totalDomainN: Int,
    val engineMode: String,
    val playedTracksJson: String, // Truncated list of Invariant IDs actually heard
    val slotMatches: Int,
    val repeatedTransitions: Int,
    val repetitivenessScore: Float
)

@Entity(tableName = "played_tracks_history")
data class PlayedTrackEntity(
    @PrimaryKey val trackId: String,
    val invariantId: Int,
    val lastPlayedTimestamp: Long = System.currentTimeMillis()
)
