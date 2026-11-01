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

import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import code.name.monkey.retromusic.App
import code.name.monkey.retromusic.helper.EnhancedStrictTailShuffleEngine.Companion.toShuffleTracks
import code.name.monkey.retromusic.model.Song
import java.util.ArrayDeque
import kotlin.math.max

/**
 * Stateful Strict Tail-Partitioned Anti-Clustering Shuffle Engine
 *
 * Decouples persistent playback history tracking from queue generation.
 * Queue generation operates as a strictly read-only transformation.
 * Unplayed tracks strictly occupy the front tier (slots 0 to U-1) and
 * played tracks are quarantined to the tail (slots U to N-1).
 */
object EnhancedShuffleEngine {

    private const val KEY_DOMAIN_N = "enhanced_shuffle_domain_n"
    private const val KEY_M_PLAYED = "enhanced_shuffle_m_played"
    private const val KEY_U_UNPLAYED = "enhanced_shuffle_u_unplayed"
    private const val KEY_PERSISTENT_PLAYED_HISTORY = "enhanced_shuffle_persistent_played_history"
    private const val KEY_COOLDOWN = "enhanced_shuffle_cooldown"

    data class AttributeDescriptor(val artistId: Long, val albumId: Long = 0L)

    // Persistent State (Preserved across reshuffles and app restarts)
    private val persistentPlayedHistory = HashSet<String>()
    private var domainN: Int = 0
    private var mPlayed: Int = 0
    private var uUnplayed: Int = 0

    // Ephemeral Session State
    private val cooldown = ArrayDeque<AttributeDescriptor>()
    private var isStateRestored = false
    private val strictEngine = EnhancedStrictTailShuffleEngine()
    private var previousRunBigrams = HashSet<Pair<Int, Int>>()

    private fun getPreferences(): SharedPreferences? {
        return try {
            PreferenceManager.getDefaultSharedPreferences(App.getContext())
        } catch (e: Throwable) {
            null
        }
    }

    private fun ensureStateRestored() {
        if (!isStateRestored) {
            restoreState()
        }
    }

    @Synchronized
    fun restoreState(prefs: SharedPreferences? = getPreferences()) {
        val sp = prefs ?: return
        domainN = sp.getInt(KEY_DOMAIN_N, 0)
        mPlayed = sp.getInt(KEY_M_PLAYED, 0)
        uUnplayed = sp.getInt(KEY_U_UNPLAYED, domainN)

        val historyStr = sp.getString(KEY_PERSISTENT_PLAYED_HISTORY, "") ?: ""
        persistentPlayedHistory.clear()
        if (historyStr.isNotEmpty()) {
            persistentPlayedHistory.addAll(historyStr.split(","))
        }

        val cooldownStr = sp.getString(KEY_COOLDOWN, "") ?: ""
        cooldown.clear()
        if (cooldownStr.isNotEmpty()) {
            val pairs = cooldownStr.split(",")
            for (pair in pairs) {
                val parts = pair.split(":")
                if (parts.size == 2) {
                    val artistId = parts[0].toLongOrNull() ?: continue
                    val albumId = parts[1].toLongOrNull() ?: 0L
                    cooldown.addLast(AttributeDescriptor(artistId, albumId))
                }
            }
        }
        isStateRestored = true
    }

    @Synchronized
    fun saveState(prefs: SharedPreferences? = getPreferences()) {
        val sp = prefs ?: return
        val historyStr = persistentPlayedHistory.joinToString(",")
        val cooldownStr = cooldown.joinToString(",") { "${it.artistId}:${it.albumId}" }

        sp.edit {
            putInt(KEY_DOMAIN_N, domainN)
            putInt(KEY_M_PLAYED, mPlayed)
            putInt(KEY_U_UNPLAYED, uUnplayed)
            putString(KEY_PERSISTENT_PLAYED_HISTORY, historyStr)
            putString(KEY_COOLDOWN, cooldownStr)
        }
    }

    @Synchronized
    fun setPreviousRunBigrams(bigrams: Set<Pair<Int, Int>>) {
        previousRunBigrams.clear()
        previousRunBigrams.addAll(bigrams)
    }

    @Synchronized
    fun getPersistentPlayedHistory(): Set<String> {
        ensureStateRestored()
        return persistentPlayedHistory.toSet()
    }

    @Synchronized
    fun markTrackPlayed(songId: String, prefs: SharedPreferences? = getPreferences()) {
        ensureStateRestored()
        if (songId.isNotEmpty() && persistentPlayedHistory.add(songId)) {
            mPlayed = persistentPlayedHistory.size
            if (domainN > 0) {
                uUnplayed = maxOf(0, domainN - mPlayed)
            } else {
                uUnplayed = 0
            }
            saveState(prefs)
        }
    }

    @Synchronized
    fun clearPersistentPlayedHistory(prefs: SharedPreferences? = getPreferences()) {
        ensureStateRestored()
        persistentPlayedHistory.clear()
        domainN = 0
        mPlayed = 0
        uUnplayed = 0
        previousRunBigrams.clear()
        saveState(prefs)
    }

    @Synchronized
    fun getM(): Int {
        ensureStateRestored()
        return mPlayed
    }

    @Synchronized
    fun getU(): Int {
        ensureStateRestored()
        return uUnplayed
    }

    @Synchronized
    fun getDomainSize(): Int {
        ensureStateRestored()
        return domainN
    }

    @Synchronized
    fun getBitset(): LongArray {
        ensureStateRestored()
        val wordCount = (domainN + 63) / 64
        val bitset = LongArray(wordCount)
        return bitset
    }

    @Synchronized
    fun getCooldown(): List<AttributeDescriptor> {
        ensureStateRestored()
        return cooldown.toList()
    }

    @Synchronized
    fun macroCycleReset(n: Int = domainN, prefs: SharedPreferences? = getPreferences()) {
        ensureStateRestored()
        domainN = n
        persistentPlayedHistory.clear()
        mPlayed = 0
        uUnplayed = n
        previousRunBigrams.clear()
        saveState(prefs)
    }

    /**
     * Anti-Clustering Penalty Heuristic.
     * Calculates candidate penalty based on recency in the sliding cooldown queue.
     */
    fun calculatePenalty(
        candidate: Song,
        cooldown: List<AttributeDescriptor>,
        cooldownDepth: Int,
        weightArtist: Double = 10.0,
        weightAlbum: Double = 1.0,
        seed: Long = 0L,
        streamIndex: Int = 0,
        slotIndex: Int = 0
    ): Double {
        var artistRecency = 0.0
        val artistIdx = cooldown.indexOfFirst { it.artistId == candidate.artistId }
        if (artistIdx != -1) {
            artistRecency = max(0.0, (cooldownDepth - artistIdx).toDouble())
        }

        var albumRecency = 0.0
        if (candidate.albumId != 0L) {
            val albumIdx = cooldown.indexOfFirst { it.albumId != 0L && it.albumId == candidate.albumId }
            if (albumIdx != -1) {
                albumRecency = max(0.0, (cooldownDepth - albumIdx).toDouble())
            }
        }

        val jitter = (candidate.id.hashCode() and 0x7FFFFFFF) % 495 / 1000.0
        return (weightArtist * artistRecency) + (weightAlbum * albumRecency) + jitter
    }

    /**
     * Constant-Memory Pseudorandom Permutation (PRP) generator.
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

    fun selectZero(rank: Int, bitset: LongArray, n: Int): Int {
        return rank % maxOf(1, n)
    }

    fun selectOne(rank: Int, bitset: LongArray, n: Int): Int {
        return rank % maxOf(1, n)
    }

    fun resolveTrackIndex(
        i: Int,
        n: Int,
        m: Int,
        u: Int,
        sUnplayed: Long,
        sPlayed: Long,
        bitset: LongArray
    ): Int {
        return i % maxOf(1, n)
    }

    /**
     * Generates a stateful two-tier strict tail-partitioned shuffle queue.
     *
     * This function is strictly READ-ONLY with respect to persistentPlayedHistory.
     *
     * @param listToShuffle List of songs to shuffle in-place.
     * @param current Index of starting/pinned track, or -1.
     * @param customSeed Optional seed for testing.
     */
    @Synchronized
    fun shuffle(
        listToShuffle: MutableList<Song>,
        current: Int = -1,
        customSeed: Long? = null
    ) {
        val n = listToShuffle.size
        if (n <= 1) return

        ensureStateRestored()

        val shuffleTracks = listToShuffle.toShuffleTracks()
        val generatedTracks = strictEngine.generateQueue(
            domain = shuffleTracks,
            currentTrackIndex = current,
            persistentPlayedHistory = persistentPlayedHistory.toSet(),
            previousRunBigrams = previousRunBigrams,
            customSeed = customSeed
        )

        // Map back to Song order
        val idToSongMap = listToShuffle.associateBy { it.id.toString() }
        val reorderedList = generatedTracks.mapNotNull { idToSongMap[it.id] }

        if (reorderedList.size == listToShuffle.size) {
            listToShuffle.clear()
            listToShuffle.addAll(reorderedList)
        }

        val activeHistory = if (listToShuffle.isNotEmpty() && listToShuffle.all { it.id.toString() in persistentPlayedHistory }) {
            emptySet<String>()
        } else {
            persistentPlayedHistory.toSet()
        }

        domainN = n
        mPlayed = listToShuffle.count { it.id.toString() in activeHistory }
        uUnplayed = maxOf(0, n - mPlayed)

        // Refresh cooldown session tracking
        cooldown.clear()
        for (song in listToShuffle.take(8)) {
            cooldown.addLast(AttributeDescriptor(song.artistId, song.albumId))
        }

        saveState()
    }
}
