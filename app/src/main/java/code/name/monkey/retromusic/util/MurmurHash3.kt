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

package code.name.monkey.retromusic.util

/**
 * MurmurHash3 64-bit hashing utility over canonical strings and byte arrays.
 */
object MurmurHash3 {

    fun hash64(text: String, seed: Long = 0x977F239BL): Long {
        return hash64(text.toByteArray(Charsets.UTF_8), seed)
    }

    fun hash64(bytes: ByteArray, seed: Long = 0x977F239BL): Long {
        val m = 0xC6A4A7935BD1E995UL.toLong()
        var h = seed xor (bytes.size.toLong() * m)
        val r = 47

        val numBlocks = bytes.size / 8
        for (i in 0 until numBlocks) {
            val index = i * 8
            var k = (bytes[index].toLong() and 0xFF) or
                    ((bytes[index + 1].toLong() and 0xFF) shl 8) or
                    ((bytes[index + 2].toLong() and 0xFF) shl 16) or
                    ((bytes[index + 3].toLong() and 0xFF) shl 24) or
                    ((bytes[index + 4].toLong() and 0xFF) shl 32) or
                    ((bytes[index + 5].toLong() and 0xFF) shl 40) or
                    ((bytes[index + 6].toLong() and 0xFF) shl 48) or
                    ((bytes[index + 7].toLong() and 0xFF) shl 56)

            k *= m
            k = k xor (k ushr r)
            k *= m

            h = h xor k
            h *= m
        }

        val tailIndex = numBlocks * 8
        val tailLen = bytes.size and 7
        if (tailLen > 0) {
            var k = 0L
            for (i in tailLen - 1 downTo 0) {
                k = (k shl 8) or (bytes[tailIndex + i].toLong() and 0xFF)
            }
            h = h xor k
            h *= m
        }

        h = h xor (h ushr r)
        h *= m
        h = h xor (h ushr r)
        return h
    }
}
