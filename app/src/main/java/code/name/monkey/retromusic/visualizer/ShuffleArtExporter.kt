/*
 * Copyright (c) 2025 Retro Music Player.
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

package code.name.monkey.retromusic.visualizer

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import kotlin.math.max
import kotlin.math.min

object ShuffleArtExporter {

    suspend fun exportTransparentHdImage(
        context: Context,
        activeWalk: TrajectoryPath,
        historyWalks: List<TrajectoryPath>,
        playNextIndices: List<Int>,
        runId: Int,
        exportDimension: Int = 3000
    ): Uri? = withContext(Dispatchers.IO) {
        val bitmap = Bitmap.createBitmap(exportDimension, exportDimension, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)

        // 1. Calculate Global Union Bounding Box
        var gMinX = activeWalk.minX
        var gMaxX = activeWalk.maxX
        var gMinY = activeWalk.minY
        var gMaxY = activeWalk.maxY

        for (h in historyWalks) {
            if (h.minX < gMinX) gMinX = h.minX
            if (h.maxX > gMaxX) gMaxX = h.maxX
            if (h.minY < gMinY) gMinY = h.minY
            if (h.maxY > gMaxY) gMaxY = h.maxY
        }

        val pad = exportDimension * 0.08f
        val spanX = max(1f, gMaxX - gMinX)
        val spanY = max(1f, gMaxY - gMinY)
        val scale = min((exportDimension - pad * 2f) / spanX, (exportDimension - pad * 2f) / spanY)
        val midX = (gMinX + gMaxX) / 2f
        val midY = (gMinY + gMaxY) / 2f

        fun toX(rawX: Float): Float = exportDimension / 2f + (rawX - midX) * scale
        fun toY(rawY: Float): Float = exportDimension / 2f - (rawY - midY) * scale

        // 2. Render Historical Paths (Back to Front) using Recency Color Palette
        val recencyColors = listOf(
            android.graphics.Color.argb(230, 244, 63, 94),   // Index 0: k-1 (Rose/Pink)
            android.graphics.Color.argb(215, 168, 85, 247),  // Index 1: k-2 (Violet/Purple)
            android.graphics.Color.argb(200, 249, 115, 22),  // Index 2: k-3 (Orange)
            android.graphics.Color.argb(190, 16, 185, 129)   // Index 3: k-4 (Emerald)
        )

        for (i in historyWalks.indices.reversed()) {
            val hWalk = historyWalks[i]
            val color = recencyColors.getOrElse(i) { recencyColors.last() }
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                style = Paint.Style.STROKE
                strokeWidth = (exportDimension / 1000f) * 0.9f
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }

            val path = android.graphics.Path().apply {
                moveTo(toX(hWalk.x[0]), toY(hWalk.y[0]))
                for (t in 1 until hWalk.size) {
                    lineTo(toX(hWalk.x[t]), toY(hWalk.y[t]))
                }
            }
            canvas.drawPath(path, paint)
        }

        // 3. Render Active Path with Crisp Cyan Line & Subtle Shadow Glow
        val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#00F0FF")
            style = Paint.Style.STROKE
            strokeWidth = (exportDimension / 1000f) * 1.1f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            setShadowLayer((exportDimension / 1000f) * 2.5f, 0f, 0f, color)
        }

        val activePath = android.graphics.Path().apply {
            moveTo(toX(activeWalk.x[0]), toY(activeWalk.y[0]))
            for (t in 1 until activeWalk.size) {
                lineTo(toX(activeWalk.x[t]), toY(activeWalk.y[t]))
            }
        }
        canvas.drawPath(activePath, activePaint)

        // 4. Render "Play Next" Markers
        val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(70, 255, 183, 3)
            style = Paint.Style.FILL
        }
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#FFB703")
            style = Paint.Style.STROKE
            strokeWidth = (exportDimension / 1000f) * 1.4f
        }
        val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.FILL
        }

        val baseRadius = (exportDimension / 1000f) * 3.8f
        for (idx in playNextIndices) {
            if (idx < activeWalk.size) {
                val px = toX(activeWalk.x[idx])
                val py = toY(activeWalk.y[idx])
                canvas.drawCircle(px, py, baseRadius * 1.8f, haloPaint)
                canvas.drawCircle(px, py, baseRadius * 1.1f, ringPaint)
                canvas.drawCircle(px, py, baseRadius * 0.45f, corePaint)
            }
        }

        // 5. Origin Indicator
        val originPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#00F0FF")
            style = Paint.Style.FILL
        }
        canvas.drawCircle(toX(0f), toY(0f), (exportDimension / 1000f) * 2.8f, originPaint)

        // 6. Write PNG to MediaStore
        saveBitmapToMediaStore(context, bitmap, "shuffle_art_run_${runId}")
    }

    private fun saveBitmapToMediaStore(context: Context, bitmap: Bitmap, fileName: String): Uri? {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$fileName.png")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/ShuffleArt")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues) ?: return null
        resolver.openOutputStream(imageUri)?.use { outStream: OutputStream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(imageUri, contentValues, null, null)
        }
        return imageUri
    }
}
