package com.robsonmartins.androidmidisynth

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import kotlin.math.max
import kotlin.math.min

/**
 * 가로로 3개의 점이 순차적으로 바운스하는 로딩 인디케이터
 */
class ThreeDotsBounceIndicatorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.FILL
    }

    private var animator: ValueAnimator? = null
    private var t: Float = 0f // 0..1 for animation progress

    private val dotCount = 3
    private val dotRadiusRatio = 0.15f // Radius relative to view height
    private val bounceHeightRatio = 0.25f // Bounce height relative to view height (줄임)
    private val animationDuration = 1200L // ms (조금 느리게)
    private val dotSpacingRatio = 0.3f // Spacing between dots relative to view height

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        start()
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    fun start() {
        if (animator?.isRunning == true) return
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = animationDuration
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = AccelerateDecelerateInterpolator() // 더 부드러운 가속/감속
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

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val dotRadius = h * dotRadiusRatio
        val bounceHeight = h * bounceHeightRatio
        val totalDotsWidth = (dotRadius * 2 * dotCount) + (h * dotSpacingRatio * (dotCount - 1))
        val startX = (w - totalDotsWidth) / 2f + dotRadius

        for (i in 0 until dotCount) {
            val offset = i * (1f / dotCount) // 각 점의 시간 오프셋
            val currentPhase = (t + offset) % 1f

            // 부드러운 바운스 애니메이션: 사인파 사용
            val bounceProgress = kotlin.math.sin(currentPhase * kotlin.math.PI.toFloat())
            val yOffset = bounceHeight * bounceProgress

            // 점의 크기와 알파도 함께 변화 (더 자연스러운 효과)
            val scale = 0.7f + (0.3f * bounceProgress.coerceIn(0f, 1f))
            val alpha = 0.5f + (0.5f * bounceProgress.coerceIn(0f, 1f))

            val cx = startX + i * (dotRadius * 2 + h * dotSpacingRatio)
            val cy = h / 2f - yOffset // Dots bounce upwards from center

            paint.alpha = (alpha * 255f).toInt().coerceIn(0, 255)
            canvas.drawCircle(cx, cy, dotRadius * scale, paint)
        }
    }
}

