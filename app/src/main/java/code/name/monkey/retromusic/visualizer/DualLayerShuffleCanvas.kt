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

package code.name.monkey.retromusic.visualizer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

class DualLayerShuffleCanvas @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var frame: DualLayerFrame? = null
    private var activeCursorSlot: Int = 0
    private var smoothMaxRadius: Float = 5.0f

    // Pass 1: Ghost Baseline
    private val baselinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#334155")
        alpha = (255 * 0.35f).toInt()
        strokeWidth = 3.5f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(12f, 12f), 0f)
    }

    // Pass 2: Live Flow Path
    private val livePathPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF")
        strokeWidth = 5.0f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    // Pass 3: Neutral Nodes
    private val neutralNodePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#94A3B8")
        style = Paint.Style.FILL
    }

    // Pass 4: Collision Alert
    private val collisionCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF1744")
        style = Paint.Style.FILL
    }
    private val collisionHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF1744")
        alpha = (255 * 0.25f).toInt()
        style = Paint.Style.FILL
    }

    // Pass 5: Playhead
    private val playheadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFEA00")
        style = Paint.Style.FILL
    }

    fun updateFrame(newFrame: DualLayerFrame, currentCursor: Int) {
        this.frame = newFrame
        this.activeCursorSlot = currentCursor

        // Compute max radius across points
        var maxRad = 1.0f
        for (p in newFrame.livePoints) {
            val r = Math.hypot(p.x.toDouble(), p.y.toDouble()).toFloat()
            if (r > maxRad) maxRad = r
        }
        for (p in newFrame.baselinePoints) {
            val r = Math.hypot(p.x.toDouble(), p.y.toDouble()).toFloat()
            if (r > maxRad) maxRad = r
        }

        // Exponential smoothing on viewport bounds to eliminate zoom jitter
        this.smoothMaxRadius = (this.smoothMaxRadius * 0.85f) + (maxRad * 0.15f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val f = frame ?: return
        val w = width.toFloat()
        val h = height.toFloat()
        val originX = w / 2f
        val originY = h / 2f

        val padding = w * 0.08f
        val usableHalf = (minOf(w, h) / 2f) - padding
        val scale = usableHalf / smoothMaxRadius.coerceAtLeast(1.0f)

        fun toScreen(pt: PointF): PointF =
            PointF(originX + (pt.x * scale), originY - (pt.y * scale))

        // PASS 1: Ghost Baseline
        if (f.baselinePoints.size > 1) {
            val basePath = buildSpline(f.baselinePoints, ::toScreen)
            canvas.drawPath(basePath, baselinePaint)
        }

        // PASS 2: Live Flow Top Layer
        if (f.livePoints.size > 1) {
            val livePath = buildSpline(f.livePoints, ::toScreen)
            canvas.drawPath(livePath, livePathPaint)
        }

        // PASS 3 & 4: Track Nodes & Collision Overlays
        for (node in f.liveNodes) {
            val screenPos = toScreen(node.position)
            if (node.isCollisionWithPrevious) {
                // Pass 4: Collision Alert Marker
                canvas.drawCircle(screenPos.x, screenPos.y, 18f, collisionHaloPaint)
                canvas.drawCircle(screenPos.x, screenPos.y, 9f, collisionCorePaint)
            } else {
                // Pass 3: Standard Node
                canvas.drawCircle(screenPos.x, screenPos.y, 4.5f, neutralNodePaint)
            }
        }

        // PASS 5: Active Playhead
        if (activeCursorSlot in f.liveNodes.indices) {
            val activePos = toScreen(f.liveNodes[activeCursorSlot].position)
            canvas.drawCircle(activePos.x, activePos.y, 14f, playheadPaint)
        }

        // Anchor origin dot
        canvas.drawCircle(originX, originY, 6f, neutralNodePaint)
    }

    private fun buildSpline(points: List<PointF>, transform: (PointF) -> PointF): Path {
        val path = Path()
        if (points.isEmpty()) return path
        val p0 = transform(points[0])
        path.moveTo(p0.x, p0.y)

        for (i in 0 until points.size - 1) {
            val pPrev = transform(points[maxOf(0, i - 1)])
            val pCurr = transform(points[i])
            val pNext = transform(points[i + 1])
            val pAfter = transform(points[minOf(points.size - 1, i + 2)])

            val cp1x = pCurr.x + (pNext.x - pPrev.x) / 6f
            val cp1y = pCurr.y + (pNext.y - pPrev.y) / 6f
            val cp2x = pNext.x - (pAfter.x - pCurr.x) / 6f
            val cp2y = pNext.y - (pAfter.y - pCurr.y) / 6f

            path.cubicTo(cp1x, cp1y, cp2x, cp2y, pNext.x, pNext.y)
        }
        return path
    }
}
