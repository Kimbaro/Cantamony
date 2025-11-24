package com.robsonmartins.androidmidisynth

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class CircularGaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val backgroundPaint = Paint().apply {
        color = 0xFF333333.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 12f
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
    }

    private val progressPaint = Paint().apply {
        color = 0xFF1db954.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 12f
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
    }

    private var max: Int = 100
    private var progress: Int = 0

    private val rectF = RectF()

    fun setMax(max: Int) {
        this.max = max
        invalidate()
    }

    fun setProgress(progress: Int) {
        this.progress = progress.coerceIn(0, max)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerX = width / 2f
        val centerY = height / 2f
        val radius = (minOf(width, height) / 2f) - backgroundPaint.strokeWidth / 2f

        rectF.set(
            centerX - radius,
            centerY - radius,
            centerX + radius,
            centerY + radius
        )

        // 배경 원 그리기 (항상 표시)
        canvas.drawArc(rectF, -90f, 360f, false, backgroundPaint)

        // 진행 원 그리기
        if (progress > 0 && max > 0) {
            val sweepAngle = (progress.toFloat() / max.toFloat()) * 360f
            canvas.drawArc(rectF, -90f, sweepAngle, false, progressPaint)
        }
    }
}

