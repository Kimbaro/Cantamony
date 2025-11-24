package com.robsonmartins.androidmidisynth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AlphaAnimation
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*

class SplashActivity : AppCompatActivity() {

    private lateinit var backgroundImage: ImageView
    private lateinit var logoImage: ImageView
    private lateinit var loadingBar: CircularGaugeView
    private lateinit var loadingText: TextView
    private lateinit var blurOverlay: View

    // 배경 이미지 리소스 ID 배열
    private val backgroundImages = arrayOf(
        R.drawable.bg1
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // View 초기화
        backgroundImage = findViewById(R.id.splashBackground)
        logoImage = findViewById(R.id.splashLogo)
        loadingBar = findViewById(R.id.splashLoadingBar)
        loadingText = findViewById(R.id.splashLoadingText)
        blurOverlay = findViewById(R.id.blurOverlay)

        // 배경 이미지 랜덤 선택
        val randomBackground = backgroundImages.random()
        backgroundImage.setImageResource(randomBackground)

        // 초기 상태 설정
        logoImage.alpha = 0f
        loadingBar.alpha = 1f  // 게이지는 바로 보이도록
        loadingBar.visibility = View.VISIBLE
        loadingBar.setMax(100)
        loadingBar.setProgress(0)
        loadingText.alpha = 1f  // 텍스트도 바로 보이도록
        blurOverlay.alpha = 0f

        // 애니메이션 시작
        startSplashAnimation()
    }

    private fun startSplashAnimation() {
        // 1. 배경 이미지 표시 (즉시)
        backgroundImage.visibility = View.VISIBLE

        // 2. 블러 효과 점진적 적용 (500ms)
        val blurAnimation = AlphaAnimation(0f, 1f).apply {
            duration = 500
            fillAfter = true
        }
        blurOverlay.startAnimation(blurAnimation)

        // 3. 블러 효과를 점진적으로 적용하면서 로고와 로딩바 표시
        CoroutineScope(Dispatchers.Main).launch {
            // 블러 효과 점진적 적용
            for (i in 0..10) {
                val blurRadius = i * 2f // 0부터 20까지
                applyBlurEffect(blurRadius)
                delay(50)
            }

            // 로고 페이드 인 (300ms)
            val logoFadeIn = AlphaAnimation(0f, 1f).apply {
                duration = 300
                fillAfter = true
            }
            logoImage.startAnimation(logoFadeIn)

            delay(200)

            // 게이지와 텍스트는 이미 보이므로 애니메이션 불필요
            // 로딩바 진행 애니메이션 바로 시작

            // 로딩바 진행 애니메이션 (0 → 100%)
            animateLoadingBar().join() // 로딩바 애니메이션 완료 대기

            // 로딩 완료 후 SelectMidiActivity로 이동
            delay(300) // 로딩바가 100% 도달 후 약간의 딜레이
            navigateToMain()
        }
    }

    private fun applyBlurEffect(radius: Float) {
        // 간단한 블러 효과를 위해 반투명 오버레이 사용
        // 실제 블러 효과를 원하면 RenderScript 또는 다른 라이브러리 필요
        val alpha = (radius / 20f).coerceIn(0f, 0.7f)
        blurOverlay.alpha = alpha
    }

    private fun animateLoadingBar(): Job {
        loadingBar.setMax(100)
        return CoroutineScope(Dispatchers.Main).launch {
            // 0에서 100%까지 부드럽게 진행 (약 2.5초)
            for (progress in 0..100) {
                loadingBar.setProgress(progress)
                // 초반에는 빠르게, 후반에는 느리게 (더 자연스러운 느낌)
                val delayTime = when {
                    progress < 30 -> 25L  // 초반 빠르게
                    progress < 70 -> 30L  // 중반 보통
                    else -> 35L           // 후반 느리게
                }
                delay(delayTime)
            }
        }
    }

    private fun navigateToMain() {
        val intent = Intent(this, SelectMidiActivity::class.java)
        startActivity(intent)
        finish()
    }
}

