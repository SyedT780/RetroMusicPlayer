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

import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

@Composable
fun InteractiveShuffleArtCanvas(
    currentRecord: ShuffleVisualRecord,
    historicalRecords: List<ShuffleVisualRecord>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()

    // 1. Precompute Coordinates
    val currentWalk = remember(currentRecord.permutedTrackIndices) {
        TrajectoryMath.computeWalk(currentRecord.permutedTrackIndices)
    }
    val historyWalks = remember(historicalRecords) {
        historicalRecords.take(4).map { TrajectoryMath.computeWalk(it.permutedTrackIndices) }
    }

    // 2. Viewport Transform State
    val zoomScale = remember { Animatable(1.0f) }
    val panOffsetX = remember { Animatable(0.0f) }
    val panOffsetY = remember { Animatable(0.0f) }

    fun animateFitToView() {
        coroutineScope.launch {
            launch { zoomScale.animateTo(1.0f, tween(260, easing = FastOutSlowInEasing)) }
            launch { panOffsetX.animateTo(0.0f, tween(260, easing = FastOutSlowInEasing)) }
            launch { panOffsetY.animateTo(0.0f, tween(260, easing = FastOutSlowInEasing)) }
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(currentRecord.runId) {
                coroutineScope {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var isMultiTouch = false
                        var lastTapTime = 0L

                        do {
                            val event = awaitPointerEvent()
                            val pointerCount = event.changes.size

                            if (pointerCount >= 2) {
                                isMultiTouch = true
                                val zoomDelta = event.calculateZoom()
                                val panDelta = event.calculatePan()

                                launch {
                                    zoomScale.snapTo((zoomScale.value * zoomDelta).coerceIn(0.5f, 10.0f))
                                    panOffsetX.snapTo(panOffsetX.value + panDelta.x)
                                    panOffsetY.snapTo(panOffsetY.value + panDelta.y)
                                }
                                event.changes.forEach { it.consume() }
                            } else if (pointerCount == 1 && !isMultiTouch) {
                                val panDelta = event.calculatePan()
                                if (panDelta != Offset.Zero) {
                                    launch {
                                        panOffsetX.snapTo(panOffsetX.value + panDelta.x)
                                        panOffsetY.snapTo(panOffsetY.value + panDelta.y)
                                    }
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        } while (event.changes.any { it.pressed })

                        // Double Tap Detection
                        val now = System.currentTimeMillis()
                        if (now - lastTapTime < 280) {
                            animateFitToView()
                            lastTapTime = 0L
                        } else {
                            lastTapTime = now
                        }
                    }
                }
            }
            .pointerInput(currentRecord.runId) {
                detectTapGestures(
                    onDoubleTap = { animateFitToView() },
                    onLongPress = {
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        Toast.makeText(context, "Exporting HD Transparent Artwork...", Toast.LENGTH_SHORT).show()
                        coroutineScope.launch {
                            val uri = ShuffleArtExporter.exportTransparentHdImage(
                                context = context,
                                activeWalk = currentWalk,
                                historyWalks = historyWalks,
                                playNextIndices = currentRecord.manualPlayNextIndices,
                                runId = currentRecord.runId,
                                exportDimension = 3000
                            )
                            if (uri != null) {
                                Toast.makeText(context, "Saved to Pictures/ShuffleArt!", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Export failed", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            }
    ) {
        val width = size.width
        val height = size.height

        // 3. Compute Global Bounding Box across all layers
        var gMinX = currentWalk.minX
        var gMaxX = currentWalk.maxX
        var gMinY = currentWalk.minY
        var gMaxY = currentWalk.maxY

        for (h in historyWalks) {
            if (h.minX < gMinX) gMinX = h.minX
            if (h.maxX > gMaxX) gMaxX = h.maxX
            if (h.minY < gMinY) gMinY = h.minY
            if (h.maxY > gMaxY) gMaxY = h.maxY
        }

        val pad = 24.dp.toPx()
        val spanX = max(1f, gMaxX - gMinX)
        val spanY = max(1f, gMaxY - gMinY)
        val baseScale = min((width - pad * 2f) / spanX, (height - pad * 2f) / spanY)
        val effectiveScale = baseScale * zoomScale.value
        val midX = (gMinX + gMaxX) / 2f
        val midY = (gMinY + gMaxY) / 2f

        fun toScreen(rawX: Float, rawY: Float): Offset {
            return Offset(
                x = width / 2f + panOffsetX.value + (rawX - midX) * effectiveScale,
                y = height / 2f + panOffsetY.value - (rawY - midY) * effectiveScale
            )
        }

        // 4. Distinct Recency-Based Layer Color Palette
        // Index 0: k-1 (most recent past run) -> Vibrant Rose/Pink
        // Index 1: k-2 (2 runs ago)            -> Violet/Purple
        // Index 2: k-3 (3 runs ago)            -> Electric Amber/Orange
        // Index 3: k-4 (4 runs ago)            -> Emerald Green
        val recencyPalette = listOf(
            Color(0xFFF43F5E).copy(alpha = 0.90f),
            Color(0xFFA855F7).copy(alpha = 0.85f),
            Color(0xFFF97316).copy(alpha = 0.80f),
            Color(0xFF10B981).copy(alpha = 0.75f)
        )

        // Render historical layers from oldest to newest (back to front)
        for (hIndex in historyWalks.indices.reversed()) {
            val hWalk = historyWalks[hIndex]
            val color = recencyPalette.getOrElse(hIndex) { recencyPalette.last() }
            val path = Path().apply {
                val start = toScreen(hWalk.x[0], hWalk.y[0])
                moveTo(start.x, start.y)
                for (t in 1 until hWalk.size) {
                    val pt = toScreen(hWalk.x[t], hWalk.y[t])
                    lineTo(pt.x, pt.y)
                }
            }
            drawPath(
                path = path,
                color = color,
                style = Stroke(width = 0.9.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }

        // 5. Render Active Run with Refined Cyan Hairline & Subtle Glow
        val activePath = Path().apply {
            val start = toScreen(currentWalk.x[0], currentWalk.y[0])
            moveTo(start.x, start.y)
            for (t in 1 until currentWalk.size) {
                val pt = toScreen(currentWalk.x[t], currentWalk.y[t])
                lineTo(pt.x, pt.y)
            }
        }

        drawIntoCanvas { canvas ->
            val frameworkPaint = Paint().apply {
                color = Color(0xFF00F0FF)
                style = PaintingStyle.Stroke
                strokeWidth = 1.0.dp.toPx()
                strokeCap = StrokeCap.Round
                strokeJoin = StrokeJoin.Round
                asFrameworkPaint().setShadowLayer(2.5.dp.toPx(), 0f, 0f, android.graphics.Color.parseColor("#00F0FF"))
            }
            canvas.nativeCanvas.drawPath(activePath.asAndroidPath(), frameworkPaint.asFrameworkPaint())
        }

        // 6. Render Manual "Play Next" Markers
        val playNextIndices = currentRecord.manualPlayNextIndices
        for (idx in playNextIndices) {
            if (idx < currentWalk.size) {
                val center = toScreen(currentWalk.x[idx], currentWalk.y[idx])
                drawCircle(color = Color(0xFFFFB703).copy(alpha = 0.28f), radius = 5.5.dp.toPx(), center = center)
                drawCircle(color = Color(0xFFFFB703), radius = 3.2.dp.toPx(), center = center, style = Stroke(width = 1.2.dp.toPx()))
                drawCircle(color = Color.White, radius = 1.5.dp.toPx(), center = center)
            }
        }

        // 7. Origin Marker Dot
        drawCircle(color = Color(0xFF00F0FF), radius = 2.5.dp.toPx(), center = toScreen(0f, 0f))
    }
}
