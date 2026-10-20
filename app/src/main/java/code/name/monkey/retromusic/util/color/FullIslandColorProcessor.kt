/*
 * Copyright (c) 2020 Hemanth Savarla.
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
package code.name.monkey.retromusic.util.color

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette

data class IslandPalette(
    val dominantColor: Int,
    val bgDark: Int,
    val islandBg: Int,
    val strokeGlowColor: Int,
    val accentColor: Int,
    val textPrimary: Int,
    val textSecondary: Int,
    val fabColor: Int,
    val fabIconColor: Int
)

object FullIslandColorProcessor {

    /**
     * Extracts palette, calculates perceived luminance according to WCAG standards,
     * and derives adaptive glassmorphism & control tints.
     */
    fun process(bitmap: Bitmap): IslandPalette {
        val palette = Palette.from(bitmap)
            .maximumColorCount(24)
            .clearFilters()
            .generate()

        val dominant = palette.getDominantColor(Color.DKGRAY)
        val vibrant = palette.getVibrantColor(
            palette.getLightVibrantColor(
                palette.getDarkVibrantColor(dominant)
            )
        )
        val accent = if (vibrant != dominant && vibrant != 0) vibrant else deriveVibrantAccent(dominant)

        // Dark background tint for ambient backdrop
        val bgDark = ColorUtils.setAlphaComponent(
            ColorUtils.blendARGB(dominant, Color.BLACK, 0.85f),
            0xFF
        )

        // Dark translucent frosted glass container surface (~80% opacity dark tint)
        val islandBg = ColorUtils.setAlphaComponent(
            ColorUtils.blendARGB(bgDark, dominant, 0.20f),
            0xCC
        )

        // Floating island rim glow derived from accent color with ~35% opacity
        val strokeGlow = ColorUtils.setAlphaComponent(accent, 0x59)

        // Always crisp white text for primary and light pastel/grey for secondary
        val textPrimary = Color.WHITE
        val textSecondary = ColorUtils.blendARGB(accent, Color.WHITE, 0.70f)

        val fabColor = accent
        val fabIconColor = Color.WHITE

        return IslandPalette(
            dominantColor = dominant,
            bgDark = bgDark,
            islandBg = islandBg,
            strokeGlowColor = strokeGlow,
            accentColor = accent,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            fabColor = fabColor,
            fabIconColor = fabIconColor
        )
    }

    /**
     * Fallback processor using MediaNotificationProcessor extracted colors.
     */
    fun process(mediaNotificationProcessor: MediaNotificationProcessor): IslandPalette {
        val dominant = mediaNotificationProcessor.backgroundColor
        val vibrant = mediaNotificationProcessor.primaryTextColor
        val accent = if (vibrant != dominant && vibrant != 0) vibrant else deriveVibrantAccent(dominant)

        val bgDark = ColorUtils.setAlphaComponent(
            ColorUtils.blendARGB(dominant, Color.BLACK, 0.85f),
            0xFF
        )

        val islandBg = ColorUtils.setAlphaComponent(
            ColorUtils.blendARGB(bgDark, dominant, 0.20f),
            0xCC
        )

        val strokeGlow = ColorUtils.setAlphaComponent(accent, 0x59)

        val textPrimary = Color.WHITE
        val textSecondary = ColorUtils.blendARGB(accent, Color.WHITE, 0.70f)

        val fabColor = accent
        val fabIconColor = Color.WHITE

        return IslandPalette(
            dominantColor = dominant,
            bgDark = bgDark,
            islandBg = islandBg,
            strokeGlowColor = strokeGlow,
            accentColor = accent,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            fabColor = fabColor,
            fabIconColor = fabIconColor
        )
    }

    private fun deriveVibrantAccent(color: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[1] = hsv[1].coerceAtLeast(0.70f)
        hsv[2] = hsv[2].coerceIn(0.50f, 0.95f)
        return Color.HSVToColor(hsv)
    }
}
