package com.robsonmartins.androidmidisynth

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class WatermarkView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint().apply {
        color = 0x33CCCCCC.toInt() // 반투명 회색
        textSize = 48f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }

    var watermarkText: String = "워터마크"
        set(value) {
            field = value
            invalidate()
        }

    var textColor: Int = 0x33CCCCCC.toInt()
        set(value) {
            field = value
            paint.color = value
            invalidate()
        }

    var textSize: Float = 48f
        set(value) {
            field = value
            paint.textSize = value
            invalidate()
        }

    var rotationAngle: Float = -45f
        set(value) {
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val width = width.toFloat()
        val height = height.toFloat()
        
        // 텍스트 크기 측정
        val textBounds = android.graphics.Rect()
        paint.getTextBounds(watermarkText, 0, watermarkText.length, textBounds)
        val textWidth = textBounds.width().toFloat()
        val textHeight = textBounds.height().toFloat()
        
        // 대각선 간격 계산 (텍스트 크기의 1.5배)
        val spacing = textWidth * 1.5f
        
        // 대각선 방향으로 반복 그리기
        // 화면을 대각선으로 가로지르는 패턴 생성
        val diagonalLength = Math.sqrt((width * width + height * height).toDouble()).toFloat()
        val steps = (diagonalLength / spacing).toInt() + 2
        
        // 시작점에서 대각선으로 이동하면서 텍스트 그리기
        for (i in -steps..steps) {
            val offsetX = i * spacing * 0.707f // cos(45°) ≈ 0.707
            val offsetY = i * spacing * 0.707f // sin(45°) ≈ 0.707
            
            // 화면 내부에 있는지 확인
            if (offsetX > -width && offsetX < width * 2 && 
                offsetY > -height && offsetY < height * 2) {
                
                canvas.save()
                canvas.translate(offsetX + width / 2, offsetY + height / 2)
                canvas.rotate(rotationAngle)
                canvas.drawText(watermarkText, 0f, 0f, paint)
                canvas.restore()
            }
        }
    }
}

