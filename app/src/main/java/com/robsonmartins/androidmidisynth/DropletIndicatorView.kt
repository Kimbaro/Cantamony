package com.robsonmartins.androidmidisynth

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.max
import kotlin.math.min

/**
 * 아래로 "물방울이 떨어지는" 느낌의 스와이프 힌트 인디케이터.
 * - 위에서 아래로 작은 원(물방울)이 반복적으로 흘러내린다.
 */
class DropletIndicatorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.FILL
    }

    private var animator: ValueAnimator? = null
    private var t: Float = 0f // 0..1

    fun start() {
        if (animator?.isRunning == true) return
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 900L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = LinearInterpolator()
            addUpdateListener {
                t = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun stop() {
        animator?.cancel()
        animator = null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // 기본적으로 자동 재생(필요 시 Splash에서 start/stop으로 제어)
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // 중앙 정렬, 위->아래로 3개의 물방울이 시간차를 두고 떨어짐
        val cx = w / 2f
        val rBase = min(w, h) * 0.10f
        val travel = h * 0.78f
        val top = (h - travel) / 2f

        fun drawDrop(phase: Float, scale: Float) {
            // phase: 0..1
            val y = top + (phase * travel)
            // 위쪽에서 튀는 느낌: 처음엔 작고 희미, 중간에 가장 진하고 큼, 아래에서 다시 희미
            val alpha = when {
                phase < 0.15f -> (phase / 0.15f) * 0.9f
                phase > 0.85f -> ((1f - phase) / 0.15f) * 0.9f
                else -> 0.9f
            }
            val r = rBase * scale
            paint.alpha = (alpha * 255f).toInt().coerceIn(0, 255)
            canvas.drawCircle(cx, y, r, paint)
        }

        // 3개의 드롭(시간차)
        val p1 = t
        val p2 = (t + 0.33f) % 1f
        val p3 = (t + 0.66f) % 1f

        // scale은 중간에서 살짝 커지도록
        fun scaleFor(p: Float): Float {
            val mid = 0.5f
            val dist = kotlin.math.abs(p - mid)
            return 0.85f + (1f - min(1f, dist / mid)) * 0.35f
        }

        // 아래쪽 잔상 느낌으로 p3->p2->p1 순서로 그리면 자연스럽게 겹침
        drawDrop(p3, scaleFor(p3))
        drawDrop(p2, scaleFor(p2))
        drawDrop(p1, scaleFor(p1))

        // 얇은 "줄기" 느낌(선택): 물방울이 지나간 흔적
        paint.alpha = (0.22f * 255f).toInt()
        val stemW = max(2f, w * 0.06f)
        canvas.drawRoundRect(
            cx - stemW / 2f,
            top,
            cx + stemW / 2f,
            top + travel,
            stemW / 2f,
            stemW / 2f,
            paint
        )
        paint.alpha = 255
    }
}





