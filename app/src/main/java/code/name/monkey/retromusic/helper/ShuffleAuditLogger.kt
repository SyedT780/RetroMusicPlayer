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

import android.os.Environment
import android.util.Log
import code.name.monkey.retromusic.App
import code.name.monkey.retromusic.model.Song
import java.io.File
import java.util.Locale

/**
 * Backend logger that records shuffle history and generates statistical randomness audit reports.
 * Supports both Normal (Fisher-Yates) and Enhanced (Hybrid Anti-Clustering) shuffle modes.
 */
object ShuffleAuditLogger {

    private const val TAG = "ShuffleAuditLogger"
    const val LOG_FILE_NAME = "shuffle_audit_log.txt"
    const val LOG_MD_NAME = "shuffle_audit_log.md"

    data class TrackAuditDescriptor(val id: Long, val artistId: Long)

    data class ShuffleRun(
        val descriptors: List<TrackAuditDescriptor>,
        val isEnhanced: Boolean
    )

    data class ShuffleRoundMetrics(
        val roundNumber: Int,
        val isEnhanced: Boolean,
        val trackCount: Int,
        val artistCollisions: Int,
        val sameSlotMatches: Int,
        val sameSlotTotal: Int,
        val sameSlotPct: Double,
        val repeatedBigramPairs: Int,
        val repeatedBigramTotal: Int,
        val repeatedBigramPct: Double,
        val repetitivenessScore: Double
    )

    data class ShuffleAuditSummary(
        val totalShuffles: Int,
        val activeMode: String,
        val totalTracks: Int,
        val cumulativeSlotMatches: Int,
        val cumulativeSlotPct: Double,
        val cumulativeBigramPairs: Int,
        val cumulativeBigramPct: Double,
        val rounds: List<ShuffleRoundMetrics>,
        val rawReportText: String
    )

    private val shuffleHistory = mutableListOf<ShuffleRun>()

    @Synchronized
    fun resetHistory() {
        shuffleHistory.clear()
    }

    @Synchronized
    fun getAuditSummary(): ShuffleAuditSummary {
        val m = shuffleHistory.size
        if (m == 0) {
            return ShuffleAuditSummary(
                totalShuffles = 0,
                activeMode = "None",
                totalTracks = 0,
                cumulativeSlotMatches = 0,
                cumulativeSlotPct = 0.0,
                cumulativeBigramPairs = 0,
                cumulativeBigramPct = 0.0,
                rounds = emptyList(),
                rawReportText = ""
            )
        }

        val lastRun = shuffleHistory.last()
        val n = lastRun.descriptors.size

        val hasEnhanced = shuffleHistory.any { it.isEnhanced }
        val hasNormal = shuffleHistory.any { !it.isEnhanced }

        val modeStr = when {
            hasEnhanced && hasNormal -> "Mixed (${if (lastRun.isEnhanced) "Enhanced" else "Normal"})"
            hasEnhanced -> "Enhanced (Anti-Clustering)"
            else -> "Normal (Fisher-Yates)"
        }

        val roundMetricsList = mutableListOf<ShuffleRoundMetrics>()

        for (i in 0 until m) {
            val run = shuffleHistory[i].descriptors
            val runIsEnhanced = shuffleHistory[i].isEnhanced
            val runSize = run.size
            var collisions = 0
            for (c in 0 until runSize - 1) {
                if (run[c].artistId == run[c + 1].artistId) {
                    collisions++
                }
            }

            if (i == 0) {
                roundMetricsList.add(
                    ShuffleRoundMetrics(
                        roundNumber = 1,
                        isEnhanced = runIsEnhanced,
                        trackCount = runSize,
                        artistCollisions = collisions,
                        sameSlotMatches = 0,
                        sameSlotTotal = runSize,
                        sameSlotPct = 0.0,
                        repeatedBigramPairs = 0,
                        repeatedBigramTotal = if (runSize > 1) runSize - 1 else 1,
                        repeatedBigramPct = 0.0,
                        repetitivenessScore = 0.0
                    )
                )
            } else {
                val prevRun = shuffleHistory[i - 1].descriptors
                val compareN = minOf(prevRun.size, runSize)

                var posMatches = 0
                for (c in 0 until compareN) {
                    if (prevRun[c].id == run[c].id) {
                        posMatches++
                    }
                }
                val posPct = if (runSize > 0) (posMatches.toDouble() / runSize) * 100.0 else 0.0

                val prevPairs = HashSet<Pair<Long, Long>>()
                for (c in 0 until prevRun.size - 1) {
                    prevPairs.add(Pair(prevRun[c].id, prevRun[c + 1].id))
                }

                var pairMatches = 0
                for (c in 0 until runSize - 1) {
                    if (prevPairs.contains(Pair(run[c].id, run[c + 1].id))) {
                        pairMatches++
                    }
                }
                val pairTotal = if (runSize > 1) runSize - 1 else 1
                val pairPct = if (runSize > 1) (pairMatches.toDouble() / pairTotal) * 100.0 else 0.0

                val score = (posPct * 0.5) + (pairPct * 0.5)

                roundMetricsList.add(
                    ShuffleRoundMetrics(
                        roundNumber = i + 1,
                        isEnhanced = runIsEnhanced,
                        trackCount = runSize,
                        artistCollisions = collisions,
                        sameSlotMatches = posMatches,
                        sameSlotTotal = runSize,
                        sameSlotPct = posPct,
                        repeatedBigramPairs = pairMatches,
                        repeatedBigramTotal = pairTotal,
                        repeatedBigramPct = pairPct,
                        repetitivenessScore = score
                    )
                )
            }
        }

        val lastIdx = m - 1
        val targetRun = shuffleHistory[lastIdx].descriptors
        val targetN = targetRun.size
        val targetPairs = (0 until targetN - 1).map { c -> Pair(targetRun[c].id, targetRun[c + 1].id) }

        val allPriorPairs = HashSet<Pair<Long, Long>>()
        val cumulativeSlotCollisions = BooleanArray(targetN)

        for (priorIdx in 0 until lastIdx) {
            val priorRun = shuffleHistory[priorIdx].descriptors
            val compareN = minOf(priorRun.size, targetN)

            for (c in 0 until compareN) {
                if (priorRun[c].id == targetRun[c].id) {
                    cumulativeSlotCollisions[c] = true
                }
            }

            val priorPSet = (0 until priorRun.size - 1).mapTo(HashSet()) { c -> Pair(priorRun[c].id, priorRun[c + 1].id) }
            allPriorPairs.addAll(priorPSet)
        }

        var totalSeenPos = 0
        for (hit in cumulativeSlotCollisions) {
            if (hit) totalSeenPos++
        }
        val totalSeenPosPct = if (targetN > 0) (totalSeenPos.toDouble() / targetN) * 100.0 else 0.0

        var totalSeenPairs = 0
        for (pair in targetPairs) {
            if (allPriorPairs.contains(pair)) {
                totalSeenPairs++
            }
        }
        val totalSeenPairsPct = if (targetN > 1) (totalSeenPairs.toDouble() / (targetN - 1)) * 100.0 else 0.0

        val reportText = generateReportText(lastRun.isEnhanced)

        return ShuffleAuditSummary(
            totalShuffles = m,
            activeMode = modeStr,
            totalTracks = n,
            cumulativeSlotMatches = totalSeenPos,
            cumulativeSlotPct = totalSeenPosPct,
            cumulativeBigramPairs = totalSeenPairs,
            cumulativeBigramPct = totalSeenPairsPct,
            rounds = roundMetricsList,
            rawReportText = reportText
        )
    }

    @Synchronized
    fun logShuffle(
        shuffledList: List<Song>,
        isEnhancedShuffle: Boolean,
        customDirectory: File? = null
    ): String {
        if (shuffledList.isEmpty()) return ""

        val descriptors = shuffledList.map { TrackAuditDescriptor(it.id, it.artistId) }
        shuffleHistory.add(ShuffleRun(descriptors, isEnhancedShuffle))

        val report = generateReportText(isEnhancedShuffle)

        val targetDir = customDirectory ?: try {
            val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            if (musicDir != null && (musicDir.exists() || musicDir.mkdirs())) {
                val testFile = File(musicDir, ".audit_write_test")
                testFile.writeText("test")
                testFile.delete()
                musicDir
            } else {
                App.getContext().getExternalFilesDir(null) ?: App.getContext().filesDir
            }
        } catch (e: Exception) {
            try {
                App.getContext().getExternalFilesDir(null) ?: App.getContext().filesDir
            } catch (ignored: Exception) {
                null
            }
        }

        if (targetDir != null) {
            try {
                if (!targetDir.exists()) {
                    targetDir.mkdirs()
                }
                val txtFile = File(targetDir, LOG_FILE_NAME)
                txtFile.writeText(report)

                val mdFile = File(targetDir, LOG_MD_NAME)
                mdFile.writeText("```text\n$report\n```")

                logD(TAG, "Shuffle audit report exported to: ${txtFile.absolutePath}")
            } catch (e: Exception) {
                logE(TAG, "Failed to write shuffle audit log", e)
            }
        }

        return report
    }

    private fun logD(tag: String, msg: String) {
        try {
            Log.d(tag, msg)
        } catch (ignored: Throwable) {
            println("[$tag] $msg")
        }
    }

    private fun logE(tag: String, msg: String, throwable: Throwable? = null) {
        try {
            Log.e(tag, msg, throwable)
        } catch (ignored: Throwable) {
            println("[$tag] $msg: ${throwable?.message}")
        }
    }

    fun generateReportText(isEnhancedShuffle: Boolean): String {
        val m = shuffleHistory.size
        if (m == 0) return ""

        val lastRun = shuffleHistory.last()
        val n = lastRun.descriptors.size

        val hasEnhanced = shuffleHistory.any { it.isEnhanced }
        val hasNormal = shuffleHistory.any { !it.isEnhanced }

        val modeStr = when {
            hasEnhanced && hasNormal -> "Mixed (Normal & Enhanced) [Latest: ${if (isEnhancedShuffle) "Enhanced" else "Normal"}]"
            hasEnhanced -> "Hybrid Anti-Clustering (Feistel PRP + Lookahead)"
            else -> "Standard Fisher-Yates (Pure Random)"
        }

        val sb = StringBuilder()
        sb.append("Mode: ").append(modeStr).append(" | Files: ").append(n).append(" | Total Shuffles: ").append(m).append("\n")
        sb.append("=".repeat(105)).append("\n")

        // PART 1
        sb.append("PART 1: ADJACENT SHUFFLE PAIRS (Step-to-Step Stability)\n")
        sb.append(String.format(Locale.US, "%-18s | %-22s | %-25s | %-15s\n", "Shuffle Pair", "Same-Slot Matches", "Repeated Bigram Pairs", "Artist Collisions"))
        sb.append("-".repeat(105)).append("\n")

        for (i in 0 until m) {
            val run = shuffleHistory[i].descriptors
            val runIsEnhanced = shuffleHistory[i].isEnhanced
            val runSize = run.size
            var collisions = 0
            for (c in 0 until runSize - 1) {
                if (run[c].artistId == run[c + 1].artistId) {
                    collisions++
                }
            }

            val modeTag = if (runIsEnhanced) "Enh" else "Norm"

            if (i == 0) {
                val pairInfo = "Shuffle #1 ($modeTag)"
                val collStr = "$collisions collisions"
                sb.append(String.format(Locale.US, "%-18s | %-22s | %-25s | %-15s\n", pairInfo, "---", "---", collStr))
            } else {
                val prevRun = shuffleHistory[i - 1].descriptors
                val compareN = minOf(prevRun.size, runSize)

                var posMatches = 0
                for (c in 0 until compareN) {
                    if (prevRun[c].id == run[c].id) {
                        posMatches++
                    }
                }
                val posPct = if (runSize > 0) (posMatches.toDouble() / runSize) * 100.0 else 0.0

                val prevPairs = HashSet<Pair<Long, Long>>()
                for (c in 0 until prevRun.size - 1) {
                    prevPairs.add(Pair(prevRun[c].id, prevRun[c + 1].id))
                }

                var pairMatches = 0
                for (c in 0 until runSize - 1) {
                    if (prevPairs.contains(Pair(run[c].id, run[c + 1].id))) {
                        pairMatches++
                    }
                }
                val pairPct = if (runSize > 1) (pairMatches.toDouble() / (runSize - 1)) * 100.0 else 0.0

                val pairInfo = "#${i} vs #${i + 1} ($modeTag)"
                val posStr = String.format(Locale.US, "%d/%d (%.1f%%)", posMatches, runSize, posPct)
                val bigramStr = String.format(Locale.US, "%d/%d (%.1f%%)", pairMatches, if (runSize > 1) runSize - 1 else 1, pairPct)
                val collStr = "$collisions collisions" + if (collisions > 0) " [!]" else ""

                sb.append(String.format(Locale.US, "%-18s | %-22s | %-25s | %-15s\n", pairInfo, posStr, bigramStr, collStr))
            }
        }

        if (m <= 1) {
            return sb.toString()
        }

        // PART 2
        val lastIdx = m - 1
        val targetRun = shuffleHistory[lastIdx].descriptors
        val targetN = targetRun.size
        val targetPairs = (0 until targetN - 1).map { c -> Pair(targetRun[c].id, targetRun[c + 1].id) }

        sb.append("\n").append("=".repeat(105)).append("\n")
        sb.append("PART 2: COMPARISON OF SHUFFLE #$m AGAINST EVERY INDIVIDUAL PRIOR RUN\n")
        sb.append(String.format(Locale.US, "%-18s | %-22s | %-25s | %-15s\n", "Comparison Target", "Same-Slot Matches", "Repeated Bigram Pairs", "Correlation Status"))
        sb.append("-".repeat(105)).append("\n")

        val allPriorPairs = HashSet<Pair<Long, Long>>()
        val cumulativeSlotCollisions = BooleanArray(targetN)

        for (priorIdx in 0 until lastIdx) {
            val priorRun = shuffleHistory[priorIdx].descriptors
            val compareN = minOf(priorRun.size, targetN)

            var posM = 0
            for (c in 0 until compareN) {
                if (priorRun[c].id == targetRun[c].id) {
                    posM++
                    cumulativeSlotCollisions[c] = true
                }
            }
            val posP = if (targetN > 0) (posM.toDouble() / targetN) * 100.0 else 0.0

            val priorPSet = (0 until priorRun.size - 1).mapTo(HashSet()) { c -> Pair(priorRun[c].id, priorRun[c + 1].id) }
            allPriorPairs.addAll(priorPSet)

            var pairM = 0
            for (pair in targetPairs) {
                if (priorPSet.contains(pair)) {
                    pairM++
                }
            }
            val pairP = if (targetN > 1) (pairM.toDouble() / (targetN - 1)) * 100.0 else 0.0

            val compLabel = "#$m vs #${priorIdx + 1}"
            val posStr = String.format(Locale.US, "%d/%d (%.1f%%)", posM, targetN, posP)
            val bigramStr = String.format(Locale.US, "%d/%d (%.1f%%)", pairM, if (targetN > 1) targetN - 1 else 1, pairP)
            val status = if (pairP < 2.0) "Nominal" else "High Overlap [!]"

            sb.append(String.format(Locale.US, "%-18s | %-22s | %-25s | %-15s\n", compLabel, posStr, bigramStr, status))
        }

        // PART 3
        var totalSeenPos = 0
        for (hit in cumulativeSlotCollisions) {
            if (hit) totalSeenPos++
        }
        val totalSeenPosPct = if (targetN > 0) (totalSeenPos.toDouble() / targetN) * 100.0 else 0.0

        var totalSeenPairs = 0
        for (pair in targetPairs) {
            if (allPriorPairs.contains(pair)) {
                totalSeenPairs++
            }
        }
        val totalSeenPairsPct = if (targetN > 1) (totalSeenPairs.toDouble() / (targetN - 1)) * 100.0 else 0.0

        sb.append("-".repeat(105)).append("\n")
        sb.append("CUMULATIVE SESSION AUDIT FOR SHUFFLE #$m:\n")
        sb.append(String.format(Locale.US, "  * Slots in Shuffle #%d previously occupied by the same track in ANY run (1..%d): %d/%d (%.1f%%)\n", m, m - 1, totalSeenPos, targetN, totalSeenPosPct))
        sb.append(String.format(Locale.US, "  * Transitions in Shuffle #%d that have occurred in ANY prior run (1..%d): %d/%d (%.1f%%)\n", m, m - 1, totalSeenPairs, if (targetN > 1) targetN - 1 else 1, totalSeenPairsPct))

        return sb.toString()
    }
}
