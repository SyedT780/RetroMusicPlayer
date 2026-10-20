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

import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import android.graphics.PointF
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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// ============================================================================
// INTERACTIVE CANVAS RENDER COMPONENT
// ============================================================================

class InteractiveShuffleArtCanvasRenderer {

    private val pathScratch = AndroidPath()
    private val bridgePathScratch = AndroidPath()

    private fun getStrataWidth(index: Int): Float {
        return when (index) {
            0 -> 1.8f // k-1: Summer Lichen
            1 -> 1.4f // k-2: Autumn Ochre Bark
            2 -> 1.2f // k-3: Detritus Loam
            3 -> 1.0f // k-4: Weathered Granite
            else -> max(0.8f, 0.9f - (index - 4) * 0.04f)
        }
    }

    fun render(
        canvas: AndroidCanvas,
        width: Int,
        height: Int,
        activeRun: ShuffleVisualRecord,
        historyRuns: List<ShuffleVisualRecord>,
        zoomScale: Float = 1.0f,
        panOffsetX: Float = 0.0f,
        panOffsetY: Float = 0.0f
    ) {
        // Deep subterranean background
        canvas.drawColor(AndroidColor.parseColor("#0A0D0A"))

        // 1. RADIAL VIEWPORT SCALING FOCUSED ON ACTIVE RUN (Zoomed to fill canvas)
        val activeRadius = activeRun.walk.maxRadius.coerceAtLeast(1.0f)
        val targetRadius = if (historyRuns.isEmpty()) {
            activeRadius
        } else {
            var maxHRadius = activeRadius
            for (h in historyRuns) {
                if (h.walk.maxRadius > maxHRadius) maxHRadius = h.walk.maxRadius
            }
            // Cap target radius so active run occupies ~85-90% of viewport without being squished by distant historical outliers
            min(maxHRadius, activeRadius * 1.15f)
        }

        val padding = width * 0.04f
        val usableHalfDim = (min(width, height) / 2f) - padding
        val baseScale = usableHalfDim / targetRadius
        val effectiveScale = baseScale * zoomScale

        // Center origin is pinned to screen center + user pan
        val originScreenX = (width / 2f) + panOffsetX
        val originScreenY = (height / 2f) + panOffsetY

        fun toScreen(rawX: Float, rawY: Float): PointF {
            return PointF(
                originScreenX + (rawX * effectiveScale),
                originScreenY - (rawY * effectiveScale) // Y inverted for screen coordinate space
            )
        }

        // 2. AUDIT FOR TWIN RESONANCE ACROSS HISTORY
        var peakTwinAffinity: TwinAffinityResult? = null
        for ((idx, hRecord) in historyRuns.withIndex()) {
            val result = TrajectoryMath.computeTwinAffinity(activeRun.walk, hRecord.walk, idx + 1)
            if (peakTwinAffinity == null || result.similarityPercentage > peakTwinAffinity.similarityPercentage) {
                peakTwinAffinity = result
            }
        }

        // 3. RENDER HISTORICAL GENERATIONS (Oldest to Newest)
        val historyPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            style = AndroidPaint.Style.STROKE
            strokeCap = AndroidPaint.Cap.ROUND
            strokeJoin = AndroidPaint.Join.ROUND
        }

        val branchPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            style = AndroidPaint.Style.STROKE
            strokeCap = AndroidPaint.Cap.ROUND
            strokeJoin = AndroidPaint.Join.ROUND
        }

        for (i in historyRuns.indices.reversed()) {
            val hRecord = historyRuns[i]
            val hColor = CanvasTrajectoryRenderer.getStrataColor(i, zoomScale)
            val hWidth = getStrataWidth(i)

            // Draw historical micro-branchlets
            branchPaint.color = hColor
            branchPaint.strokeWidth = hWidth * 0.6f
            for (b in hRecord.walk.branchOffsets) {
                val src = toScreen(hRecord.walk.x[b.srcIdx], hRecord.walk.y[b.srcIdx])
                val dst = toScreen(b.bx, b.by)
                canvas.drawLine(src.x, src.y, dst.x, dst.y, branchPaint)
            }

            // Draw historical tendril body
            historyPaint.color = hColor
            historyPaint.strokeWidth = hWidth

            CanvasTrajectoryRenderer.buildSmoothSplinePath(pathScratch, hRecord.walk, ::toScreen, closeLoop = true)
            canvas.drawPath(pathScratch, historyPaint)
        }

        // 4. DRAW ANASTOMOSIS BRIDGES IF NEAR-TWIN DETECTED
        peakTwinAffinity?.let { twin ->
            if (twin.similarityPercentage >= 65.0f) {
                // Translucent warning envelope along active trajectory (clean, no heavy blur filter)
                val resonancePaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                    style = AndroidPaint.Style.STROKE
                    color = AndroidColor.parseColor("#33FACC15") // Translucent amber gold
                    strokeWidth = 4.0f
                }
                CanvasTrajectoryRenderer.buildSmoothSplinePath(pathScratch, activeRun.walk, ::toScreen, closeLoop = true)
                canvas.drawPath(pathScratch, resonancePaint)

                // Transverse connecting hyphal filaments
                val bridgePaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                    style = AndroidPaint.Style.STROKE
                    color = AndroidColor.parseColor("#FDDE47")
                    strokeWidth = 1.4f
                }
                val nodePaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                    style = AndroidPaint.Style.FILL
                    color = AndroidColor.WHITE
                }
                val beadPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                    style = AndroidPaint.Style.FILL
                    color = AndroidColor.parseColor("#F59E0B")
                }

                for (bridge in twin.fusedBridges) {
                    val p1 = toScreen(bridge.ax, bridge.ay)
                    val p2 = toScreen(bridge.bx, bridge.by)
                    val midX = (p1.x + p2.x) / 2f + sin(bridge.step.toDouble()).toFloat() * 6f
                    val midY = (p1.y + p2.y) / 2f + cos(bridge.step.toDouble()).toFloat() * 6f

                    bridgePathScratch.reset()
                    bridgePathScratch.moveTo(p1.x, p1.y)
                    bridgePathScratch.quadTo(midX, midY, p2.x, p2.y)
                    canvas.drawPath(bridgePathScratch, bridgePaint)

                    canvas.drawCircle(p1.x, p1.y, 2.0f, nodePaint)
                    canvas.drawCircle(p2.x, p2.y, 2.0f, nodePaint)
                    canvas.drawCircle(midX, midY, 1.5f, beadPaint)
                }
            }
        }

        // 5. RENDER ACTIVE SPRING SHOOT
        // Active organic branchlets
        val activeBranchPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            style = AndroidPaint.Style.STROKE
            strokeCap = AndroidPaint.Cap.ROUND
            strokeJoin = AndroidPaint.Join.ROUND
            color = AndroidColor.parseColor("#80D9F99D") // Translucent spring bioluminescence
            strokeWidth = 0.9f
        }
        for (b in activeRun.walk.branchOffsets) {
            val src = toScreen(activeRun.walk.x[b.srcIdx], activeRun.walk.y[b.srcIdx])
            val dst = toScreen(b.bx, b.by)
            canvas.drawLine(src.x, src.y, dst.x, dst.y, activeBranchPaint)
        }

        // Active tendril body (Crisp, clean dual-pass line without heavy setShadowLayer blur)
        val isTwinWarning = peakTwinAffinity != null && peakTwinAffinity.similarityPercentage >= 80f
        val activeGlowColor = if (isTwinWarning) AndroidColor.parseColor("#FDE047") else AndroidColor.parseColor("#D9F99D")

        val activeGlowPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            style = AndroidPaint.Style.STROKE
            strokeCap = AndroidPaint.Cap.ROUND
            strokeJoin = AndroidPaint.Join.ROUND
            strokeWidth = 2.0f
            color = activeGlowColor
        }

        CanvasTrajectoryRenderer.buildSmoothSplinePath(pathScratch, activeRun.walk, ::toScreen, closeLoop = true)
        canvas.drawPath(pathScratch, activeGlowPaint)

        // Inner high-luminance core line for sharp, fine contrast
        val activeCorePaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            style = AndroidPaint.Style.STROKE
            strokeCap = AndroidPaint.Cap.ROUND
            strokeJoin = AndroidPaint.Join.ROUND
            strokeWidth = 0.9f
            color = AndroidColor.WHITE
        }
        canvas.drawPath(pathScratch, activeCorePaint)

        // 6. RENDER MANUAL "PLAY NEXT" SPORE BLOSSOMS
        val sporeHaloPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            style = AndroidPaint.Style.FILL
            color = AndroidColor.parseColor("#20F59E0B")
        }
        val sporeRingPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            style = AndroidPaint.Style.STROKE
            color = AndroidColor.parseColor("#F59E0B")
            strokeWidth = 1.2f
        }
        val sporeCorePaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            style = AndroidPaint.Style.FILL
            color = AndroidColor.WHITE
        }

        val playNextIndices = activeRun.playNextIndices.ifEmpty { activeRun.manualPlayNextIndices.toSet() }
        for (idx in playNextIndices) {
            if (idx < activeRun.walk.size) {
                val pos = toScreen(activeRun.walk.x[idx], activeRun.walk.y[idx])
                canvas.drawCircle(pos.x, pos.y, 10f, sporeHaloPaint)
                canvas.drawCircle(pos.x, pos.y, 5f, sporeRingPaint)
                canvas.drawCircle(pos.x, pos.y, 2f, sporeCorePaint)
            }
        }

        // 7. CENTER SEED ROOT (0, 0)
        val rootPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            style = AndroidPaint.Style.FILL
            color = AndroidColor.parseColor("#D9F99D")
        }
        val rootOuter = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            style = AndroidPaint.Style.STROKE
            color = AndroidColor.parseColor("#050A06")
            strokeWidth = 1.5f
        }
        canvas.drawCircle(originScreenX, originScreenY, 3.5f, rootPaint)
        canvas.drawCircle(originScreenX, originScreenY, 5.5f, rootOuter)
    }
}

@Composable
fun InteractiveShuffleArtCanvas(
    currentRecord: ShuffleVisualRecord,
    historicalRecords: List<ShuffleVisualRecord>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()
    val renderer = remember { InteractiveShuffleArtCanvasRenderer() }

    // 1. Ensure Trajectory Walk Objects are Prepared
    val activeRunPrepared = remember(currentRecord) {
        if (currentRecord.walk.size > 1) currentRecord
        else currentRecord.copy(walk = TrajectoryMath.computeWalk(currentRecord.permutedTrackIndices))
    }
    val historicalRunsPrepared = remember(historicalRecords) {
        historicalRecords.map { h ->
            if (h.walk.size > 1) h
            else h.copy(walk = TrajectoryMath.computeWalk(h.permutedTrackIndices))
        }
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
            .pointerInput(activeRunPrepared.runId) {
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
            .pointerInput(activeRunPrepared.runId) {
                detectTapGestures(
                    onDoubleTap = { animateFitToView() },
                    onLongPress = {
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        Toast.makeText(context, "Exporting HD Transparent Artwork...", Toast.LENGTH_SHORT).show()
                        coroutineScope.launch {
                            val uri = ShuffleArtExporter.exportTransparentHdImage(
                                context = context,
                                activeRun = activeRunPrepared,
                                historyRuns = historicalRunsPrepared,
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
        val width = size.width.toInt()
        val height = size.height.toInt()

        drawIntoCanvas { canvas ->
            renderer.render(
                canvas = canvas.nativeCanvas,
                width = width,
                height = height,
                activeRun = activeRunPrepared,
                historyRuns = historicalRunsPrepared,
                zoomScale = zoomScale.value,
                panOffsetX = panOffsetX.value,
                panOffsetY = panOffsetY.value
            )
        }
    }
}
