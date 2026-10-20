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
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream

object ShuffleArtExporter {

    suspend fun exportTransparentHdImage(
        context: Context,
        activeRun: ShuffleVisualRecord,
        historyRuns: List<ShuffleVisualRecord>,
        exportDimension: Int = 3000
    ): Uri? = withContext(Dispatchers.IO) {
        val bitmap = Bitmap.createBitmap(exportDimension, exportDimension, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val renderer = InteractiveShuffleArtCanvasRenderer()
        renderer.render(
            canvas = canvas,
            width = exportDimension,
            height = exportDimension,
            activeRun = activeRun,
            historyRuns = historyRuns
        )

        saveBitmapToMediaStore(context, bitmap, "shuffle_art_run_${activeRun.runId}")
    }

    suspend fun exportTransparentHdImage(
        context: Context,
        activeWalk: TrajectoryPath,
        historyWalks: List<TrajectoryPath>,
        playNextIndices: List<Int>,
        runId: Long,
        exportDimension: Int = 3000
    ): Uri? {
        val activeRun = ShuffleVisualRecord(
            runId = runId,
            walk = activeWalk,
            playNextIndices = playNextIndices.toSet()
        )
        val historyRuns = historyWalks.mapIndexed { idx, walk ->
            ShuffleVisualRecord(
                runId = runId - idx - 1,
                walk = walk
            )
        }
        return exportTransparentHdImage(
            context = context,
            activeRun = activeRun,
            historyRuns = historyRuns,
            exportDimension = exportDimension
        )
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
