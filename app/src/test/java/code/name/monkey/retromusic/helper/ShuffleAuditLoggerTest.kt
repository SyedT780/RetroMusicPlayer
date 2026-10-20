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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ShuffleAuditLoggerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Before
    fun setUp() {
        ShuffleAuditLogger.resetHistory()
    }

    private fun createDummySong(id: Long, artistId: Long, albumId: Long): Song {
        return Song(
            id = id,
            title = "Song $id",
            trackNumber = 1,
            year = 2024,
            duration = 180000,
            data = "/path/to/song_$id.mp3",
            dateModified = System.currentTimeMillis(),
            albumId = albumId,
            albumName = "Album $albumId",
            artistId = artistId,
            artistName = "Artist $artistId",
            composer = "",
            albumArtist = "",
        )
    }

    @Test
    fun testEnhancedModeReportGeneration() {
        val library = (0L..49L).map { i ->
            val artistId = i % 6
            createDummySong(id = i, artistId = artistId, albumId = artistId)
        }.toMutableList()

        var report = ""
        val targetDir = tempFolder.newFolder("audit_test_enhanced")

        for (sIdx in 0 until 8) {
            val shuffled = ArrayList(library)
            EnhancedShuffleEngine.shuffle(shuffled, current = -1, customSeed = 1000L + sIdx * 7919L)
            report = ShuffleAuditLogger.logShuffle(shuffled, isEnhancedShuffle = true, customDirectory = targetDir)
        }

        assertTrue("Report header missing mode", report.contains("Mode: Hybrid Anti-Clustering"))
        assertTrue("Report header missing files count", report.contains("Files: 50"))
        assertTrue("Report header missing total shuffles", report.contains("Total Shuffles: 8"))
        assertTrue("Report missing Part 1", report.contains("PART 1: ADJACENT SHUFFLE PAIRS"))
        assertTrue("Report missing Part 2", report.contains("PART 2: COMPARISON OF SHUFFLE #8 AGAINST EVERY INDIVIDUAL PRIOR RUN"))
        assertTrue("Report missing Part 3", report.contains("CUMULATIVE SESSION AUDIT FOR SHUFFLE #8:"))

        val txtFile = targetDir.resolve(ShuffleAuditLogger.LOG_FILE_NAME)
        val mdFile = targetDir.resolve(ShuffleAuditLogger.LOG_MD_NAME)

        assertTrue("TXT audit file not created", txtFile.exists())
        assertTrue("MD audit file not created", mdFile.exists())
        assertTrue("TXT audit file empty", txtFile.length() > 0)
        assertTrue("MD audit file empty", mdFile.length() > 0)
    }

    @Test
    fun testNormalModeReportGeneration() {
        val library = (0L..29L).map { i ->
            val artistId = i % 4
            createDummySong(id = i, artistId = artistId, albumId = artistId)
        }.toMutableList()

        var report = ""
        val targetDir = tempFolder.newFolder("audit_test_normal")

        repeat(5) {
            val shuffled = ArrayList(library)
            shuffled.shuffle()
            report = ShuffleAuditLogger.logShuffle(shuffled, isEnhancedShuffle = false, customDirectory = targetDir)
        }

        assertTrue("Report header missing normal mode", report.contains("Mode: Standard Fisher-Yates (Pure Random)"))
        assertTrue("Report header missing files count", report.contains("Files: 30"))
        assertTrue("Report header missing total shuffles", report.contains("Total Shuffles: 5"))
        assertTrue("Report missing Part 1", report.contains("PART 1: ADJACENT SHUFFLE PAIRS"))
        assertTrue("Report missing Norm tag in Part 1", report.contains("(Norm)"))

        val txtFile = targetDir.resolve(ShuffleAuditLogger.LOG_FILE_NAME)
        assertTrue("TXT audit file not created for normal mode", txtFile.exists())
        assertTrue("TXT audit file empty", txtFile.length() > 0)
    }

    @Test
    fun testMixedModeReportGeneration() {
        val library = (0L..19L).map { i ->
            val artistId = i % 3
            createDummySong(id = i, artistId = artistId, albumId = artistId)
        }.toMutableList()

        var report = ""
        val targetDir = tempFolder.newFolder("audit_test_mixed")

        for (sIdx in 0 until 4) {
            val shuffled = ArrayList(library)
            val isEnhanced = (sIdx % 2 == 1)
            if (isEnhanced) {
                EnhancedShuffleEngine.shuffle(shuffled, current = -1, customSeed = 500L + sIdx * 100L)
            } else {
                shuffled.shuffle()
            }
            report = ShuffleAuditLogger.logShuffle(shuffled, isEnhancedShuffle = isEnhanced, customDirectory = targetDir)
        }

        assertTrue("Report header missing mixed mode indication", report.contains("Mode: Mixed (Normal & Enhanced)"))
        assertTrue("Report missing Enh tag", report.contains("(Enh)"))
        assertTrue("Report missing Norm tag", report.contains("(Norm)"))
    }

    @Test
    fun testVaryingListSizes() {
        val targetDir = tempFolder.newFolder("audit_test_varying")

        val list1 = (0L..9L).map { createDummySong(id = it, artistId = it % 2, albumId = 1) }.toMutableList()
        list1.shuffle()
        ShuffleAuditLogger.logShuffle(list1, isEnhancedShuffle = false, customDirectory = targetDir)

        val list2 = (0L..24L).map { createDummySong(id = it, artistId = it % 5, albumId = 2) }.toMutableList()
        list2.shuffle()
        val report = ShuffleAuditLogger.logShuffle(list2, isEnhancedShuffle = true, customDirectory = targetDir)

        assertTrue("Report missing total shuffles count for varying sizes", report.contains("Total Shuffles: 2"))
        assertTrue("Report missing Files count for latest run", report.contains("Files: 25"))
    }

    @Test
    fun testAuditSummaryMetricsExtraction() {
        val library = (0L..19L).map { i ->
            val artistId = i % 4
            createDummySong(id = i, artistId = artistId, albumId = artistId)
        }.toMutableList()

        val targetDir = tempFolder.newFolder("audit_test_summary")

        for (sIdx in 0 until 3) {
            val shuffled = ArrayList(library)
            EnhancedShuffleEngine.shuffle(shuffled, current = -1, customSeed = 100L + sIdx * 50L)
            ShuffleAuditLogger.logShuffle(shuffled, isEnhancedShuffle = true, customDirectory = targetDir)
        }

        val summary = ShuffleAuditLogger.getAuditSummary()
        assertEquals(3, summary.totalShuffles)
        assertEquals(20, summary.totalTracks)
        assertEquals(3, summary.rounds.size)

        val round1 = summary.rounds[0]
        assertEquals(1, round1.roundNumber)
        assertTrue(round1.isEnhanced)
        assertEquals(20, round1.trackCount)

        val round2 = summary.rounds[1]
        assertEquals(2, round2.roundNumber)
        assertTrue(round2.isEnhanced)

        ShuffleAuditLogger.resetHistory()
        val emptySummary = ShuffleAuditLogger.getAuditSummary()
        assertEquals(0, emptySummary.totalShuffles)
        assertEquals(0, emptySummary.rounds.size)
    }
}
