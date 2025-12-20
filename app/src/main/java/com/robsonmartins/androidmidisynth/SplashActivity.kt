package com.robsonmartins.androidmidisynth

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.layout.ContentScale
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.effect
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.capsule.ContinuousRoundedRectangle
import com.robsonmartins.androidmidisynth.dto.DownloadState
import androidx.lifecycle.lifecycleScope
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.opacity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.log
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.text.TextStyle
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Article
import com.robsonmartins.androidmidisynth.components.LiquidBottomTab
import com.robsonmartins.androidmidisynth.components.LiquidBottomTabs
import com.robsonmartins.androidmidisynth.components.LiquidButton

class SplashActivity : AppCompatActivity() {

    private lateinit var splashRoot: View
    private lateinit var scrollHintComposeView: ComposeView
    private var currentAnimator: android.view.ViewPropertyAnimator? = null // 애니메이션 참조 저장

    private var loadingFinished = false
    private var isNavigating = false
    private var isSwipedDown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 상단 바 투명 설정
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    )
        }
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        // WindowInsetsControllerCompat 사용 (권장)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = false // 어두운 아이콘 (밝은 배경용)
        // 하단 네비게이션바 숨기기
        controller.hide(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        // 액션바/시스템바를 모두 숨겨서 완전한 전체화면 스플래시 구성
        supportActionBar?.hide()
        setContentView(R.layout.activity_splash)

        // View 초기화
        splashRoot = findViewById(R.id.splashRoot)
        scrollHintComposeView = findViewById(R.id.scrollHintComposeView)

        // ComposeView 초기화
        scrollHintComposeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        scrollHintComposeView.setContent {
            SwipeHintWithBackdrop(
                alpha = 0f,
                showLoadingText = true, // 초기에는 로딩 텍스트 표시
                isSwipedDown = isSwipedDown,
                onSwipeDown = {
                    if (loadingFinished && !isNavigating) {
                        isSwipedDown = true
                        // 텍스트를 즉시 숨기기 위해 상태 업데이트
                        updateSwipeHintAlpha(1f, showBottomNav = false) // 네비게이션바 숨김
                        // 애니메이션 후 네비게이션
                        lifecycleScope.launch {
                            delay(300) // 애니메이션 시간
                            navigateToMain()
                        }
                    }
                },
                isSwipeEnabled = loadingFinished, // 파일 다운로드 완료 후 활성화
                showBottomNav = true // 초기에는 네비게이션바 표시
            )
        }

        // 초기 상태 설정
        // 스와이프 힌트는 처음에는 숨김 (아래에서 위로 올라올 예정)
        scrollHintComposeView.visibility = View.GONE
        scrollHintComposeView.alpha = 0f
        loadingFinished = false  // 로딩 중에는 스와이프 불가

        // 애니메이션 시작
        startSplashAnimation()
    }

    private fun startSplashAnimation() {
        lifecycleScope.launch {
            // 1. 이미지 화면 보여주기 (배경 이미지는 보이도록, 스와이프 힌트는 숨김)
            if (isDestroyed || isFinishing || isNavigating) return@launch
            try {
                scrollHintComposeView.visibility = View.VISIBLE
                scrollHintComposeView.alpha = 1f // 배경 이미지가 보이도록
                updateSwipeHintAlpha(0f, showLoadingText = true) // 스와이프 힌트만 숨김, 로딩 텍스트는 표시
            } catch (e: Exception) {
                return@launch
            }
            delay(500)

            // 2. 파일 로딩 시뮬레이션 (이미 "파일을 불러오는중"이 표시되어 있음)
            if (isDestroyed || isFinishing || isNavigating) return@launch
            delay(2500) // 파일 로딩 시뮬레이션

            // 3. 스와이프하여 다음 단계 진행 (로딩 완료, 스와이프 힌트 표시)
            if (isDestroyed || isFinishing || isNavigating) return@launch
            loadingFinished = true
            showSwipeToStartHint()
        }
    }

    private fun showSwipeToStartHint() {
        if (isDestroyed || isFinishing) return
        if (!::scrollHintComposeView.isInitialized) return

        // 기존 애니메이션 취소
        try {
            currentAnimator?.cancel()
        } catch (e: Exception) {
            // 무시
        }
        currentAnimator = null

        // View 애니메이션을 완전히 제거하고 Compose 상태로만 관리
        // 이렇게 하면 DeadObjectException을 방지할 수 있습니다
        try {
            if (scrollHintComposeView.isAttachedToWindow) {
                scrollHintComposeView.visibility = View.VISIBLE
                // 애니메이션 없이 즉시 표시 (Compose 내부에서 애니메이션 처리)
                updateSwipeHintAlpha(1f)
            }
        } catch (e: Exception) {
            // View 조작 중 예외 발생 시 무시
        }
    }

    private fun updateSwipeHintAlpha(alpha: Float, showLoadingText: Boolean = false, showBottomNav: Boolean = true) {
        if (isDestroyed || isFinishing || isNavigating) return
        if (!::scrollHintComposeView.isInitialized) return

        // Window가 유효한지 확인
        if (window == null || window?.decorView == null) return

        // View가 attached되어 있는지 확인
        if (!scrollHintComposeView.isAttachedToWindow) {
            return
        }

        // Activity가 종료 중이면 setContent 호출하지 않음
        // hasWindowFocus() 체크는 제거 - onResume() 직후에는 focus가 없을 수 있음
        if (isChangingConfigurations) {
            return
        }

        try {
            // 메인 스레드에서 실행 보장
            scrollHintComposeView.post {
                // 다시 한번 모든 체크
                if (isDestroyed || isFinishing || isNavigating) return@post
                if (!::scrollHintComposeView.isInitialized) return@post
                if (window == null || window?.decorView == null) return@post
                if (!scrollHintComposeView.isAttachedToWindow) return@post
                // hasWindowFocus() 체크 제거 - onResume() 직후에는 focus가 없을 수 있음

                try {
                    scrollHintComposeView.setContent {
                        SwipeHintWithBackdrop(
                            alpha = alpha,
                            showLoadingText = showLoadingText, // 로딩 텍스트 표시 여부
                            isSwipedDown = isSwipedDown,
                            onSwipeDown = {
                                if (loadingFinished && !isNavigating && !isDestroyed && !isFinishing) {
                                    isSwipedDown = true
                                    // 텍스트를 즉시 숨기기 위해 상태 업데이트
                                    updateSwipeHintAlpha(1f, showBottomNav = false) // 네비게이션바 숨김
                                    lifecycleScope.launch {
                                        delay(300)
                                        navigateToMain()
                                    }
                                }
                            },
                            isSwipeEnabled = loadingFinished, // 파일 다운로드 완료 후 활성화
                            showBottomNav = showBottomNav // 하단 네비게이션바 표시 여부
                        )
                    }
                } catch (e: Exception) {
                    // setContent 호출 중 예외 발생 시 무시
                }
            }
        } catch (e: Exception) {
            // post 호출 중 예외 발생 시 무시
        }
    }

    private fun navigateToMain() {
        if (isNavigating || isDestroyed || isFinishing) return
        isNavigating = true

        try {
            val intent = Intent(this, NewActivity::class.java)
            startActivity(intent)

            // 투명 전환: NewActivity는 페이드 인, SplashActivity는 그대로 유지
            // NewActivity가 투명 테마를 사용하므로 자연스러운 전환 효과
            overridePendingTransition(android.R.anim.fade_in, 0)

            // SplashActivity는 종료하지 않고 back stack에 남겨,
            // NewActivity에서 Close/뒤로가기 시 앱 종료가 아니라 Splash로 돌아가게 한다.
        } catch (e: Exception) {
            // Activity가 이미 종료된 경우 예외 처리
            isNavigating = false
        }
    }

    override fun onResume() {
        super.onResume()
        if (isDestroyed || isFinishing) return

        // Window가 유효한지 확인
        if (window == null || window?.decorView == null) return

        // NewActivity를 닫고(Splash로 복귀) 다시 스와이프할 수 있도록 플래그 리셋
        isNavigating = false

        // NewActivity에서 돌아왔을 때 확장된 글래스를 되돌리기 위해 상태 리셋
        val wasSwipedDown = isSwipedDown
        if (isSwipedDown) {
            isSwipedDown = false // 스와이프 상태 리셋 - 글래스 복구 및 텍스트 다시 표시
        }

        // 로딩이 끝난 상태에서 복귀했을 때 처리
        if (loadingFinished && ::scrollHintComposeView.isInitialized) {
            try {
                if (scrollHintComposeView.isAttachedToWindow) {
                    // View가 숨겨져 있으면 다시 표시
                    if (scrollHintComposeView.visibility != View.VISIBLE) {
                        scrollHintComposeView.visibility = View.VISIBLE
                        scrollHintComposeView.alpha = 1f
                    }

                    // NewActivity에서 돌아온 경우 상태 업데이트하여 글래스 복구 및 텍스트 표시
                    // Window focus가 완전히 돌아올 때까지 약간의 딜레이를 주고 업데이트
                    scrollHintComposeView.postDelayed({
                        if (!isDestroyed && !isFinishing && !isNavigating) {
                            // isSwipedDown이 false로 리셋되었으므로 글래스가 원래 상태로 복구됨
                            // updateSwipeHintAlpha를 호출하여 Compose 상태를 업데이트
                            // 하단 네비게이션바 다시 표시
                            updateSwipeHintAlpha(1f, showBottomNav = true)
                        }
                    }, 100) // 100ms 딜레이로 Window focus 확보 대기
                }
            } catch (e: Exception) {
                // Window가 이미 파괴된 경우 무시
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Activity가 일시정지될 때 즉시 모든 작업 중단
        isNavigating = true // 네비게이션 플래그 설정하여 추가 작업 방지

        // NewActivity가 투명하게 보이도록 ComposeView는 숨기지 않음
        // 단, 애니메이션만 취소
        if (::scrollHintComposeView.isInitialized) {
            try {
                scrollHintComposeView.clearAnimation()
                // 저장된 애니메이션 참조를 사용하여 취소
                currentAnimator?.cancel()
                currentAnimator = null
                // ComposeView를 숨기지 않음 - NewActivity가 투명하게 보이도록
                // scrollHintComposeView.visibility = View.GONE
            } catch (e: Exception) {
                // 무시
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Window 포커스를 잃으면 모든 작업 중단
        if (!hasFocus) {
            isNavigating = true
            // 애니메이션 취소
            try {
                currentAnimator?.cancel()
                currentAnimator = null
            } catch (e: Exception) {
                // 무시
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Activity가 완전히 가려질 때 추가 정리 작업
        // 이는 DeadObjectException을 방지하는 데 도움이 됩니다
        if (::scrollHintComposeView.isInitialized) {
            try {
                scrollHintComposeView.clearAnimation()
                // 저장된 애니메이션 참조를 사용하여 취소
                currentAnimator?.cancel()
                currentAnimator = null
                // NewActivity가 투명하게 보이도록 ComposeView는 숨기지 않음
                // scrollHintComposeView.visibility = View.GONE
            } catch (e: Exception) {
                // 무시
            }
        }
    }

    override fun onDestroy() {
        // Activity 종료 시 모든 작업 정리
        isNavigating = true // 더 이상 네비게이션 허용하지 않음

        // 애니메이션 취소
        try {
            currentAnimator?.cancel()
        } catch (e: Exception) {
            // 무시
        }
        currentAnimator = null

        if (::scrollHintComposeView.isInitialized) {
            try {
                scrollHintComposeView.clearAnimation()
                // ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed가 
                // 자동으로 Compose 콘텐츠를 정리하므로 별도 처리가 필요 없음
            } catch (e: Exception) {
                // 무시
            }
        }

        super.onDestroy()
    }
}

/**
 * 물방울 인디케이터 (Compose 버전)
 * 위에서 아래로 물방울이 떨어지는 애니메이션
 */
@Composable
fun DropletIndicator(
    modifier: Modifier = Modifier
) {
    // 애니메이션 상태 (0f ~ 1f)
    val infiniteTransition = rememberInfiniteTransition(label = "droplet")
    val t by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 900,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "droplet_animation"
    )

    Canvas(
        modifier = modifier.size(28.dp, 44.dp)
    ) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        // 중앙 정렬, 위->아래로 3개의 물방울이 시간차를 두고 떨어짐
        val cx = w / 2f
        val rBase = kotlin.math.min(w, h) * 0.10f
        val travel = h * 0.98f
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
            drawCircle(
                color = Color(0xFF2F4F4F).copy(alpha = alpha),
                radius = r,
                center = androidx.compose.ui.geometry.Offset(cx, y)
            )
        }

        // 3개의 드롭(시간차)
        val p1 = t
        val p2 = (t + 0.33f) % 1f
        val p3 = (t + 0.66f) % 1f

        // scale은 중간에서 살짝 커지도록
        fun scaleFor(p: Float): Float {
            val mid = 0.5f
            val dist = kotlin.math.abs(p - mid)
            return 0.85f + (1f - kotlin.math.min(1f, dist / mid)) * 0.35f
        }

        // 얇은 "줄기" 느낌(선택): 물방울이 지나간 흔적
        val stemW = kotlin.math.max(2f, w * 0.06f)
        drawRoundRect(
            color = Color.White.copy(alpha = 0.22f),
            topLeft = androidx.compose.ui.geometry.Offset(
                cx - stemW / 2f,
                top
            ),
            size = androidx.compose.ui.geometry.Size(stemW, travel),
            cornerRadius = CornerRadius(stemW / 2f, stemW / 2f)
        )

        // 아래쪽 잔상 느낌으로 p3->p2->p1 순서로 그리면 자연스럽게 겹침
        drawDrop(p3, scaleFor(p3))
        drawDrop(p2, scaleFor(p2))
        drawDrop(p1, scaleFor(p1))
    }
}

/**
 * 스와이프 힌트를 표시하는 Compose 함수 (Backdrop effects 적용)
 */
@Composable
fun SwipeHintWithBackdrop(
    alpha: Float,
    showLoadingText: Boolean = false, // 로딩 텍스트 표시 여부
    isSwipedDown: Boolean = false, // 스와이프 다운 상태
    onSwipeDown: () -> Unit = {}, // 스와이프 다운 콜백 추가
    isSwipeEnabled: Boolean = false, // 스와이프 활성화 여부
    showBottomNav: Boolean = true // 하단 네비게이션바 표시 여부
) {
    var currentAlpha by remember { mutableStateOf(alpha) }

    LaunchedEffect(alpha) {
        currentAlpha = alpha
    }

    // 스와이프 진행률 상태 (0.0 ~ 1.0)
    var swipeProgress by remember { mutableFloatStateOf(0f) }

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    // 화면 높이를 가져와서 임계값으로 사용
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    // 체크보드 패턴을 Canvas Backdrop으로 생성 (좌표 독립적)
    val checkerboardBackdrop = rememberCanvasBackdrop {
        val tileSize = 50.dp.toPx() // 체크보드 타일 크기
        val width = size.width
        val height = size.height

        // 체크보드 패턴 그리기
        for (y in 0..(height / tileSize).toInt()) {
            for (x in 0..(width / tileSize).toInt()) {
                val isEven = (x + y) % 2 == 0
                val color = if (isEven) Color.White else Color.Black

                drawRect(
                    color = color,
                    topLeft = androidx.compose.ui.geometry.Offset(
                        x * tileSize,
                        y * tileSize
                    ),
                    size = androidx.compose.ui.geometry.Size(tileSize, tileSize)
                )
            }
        }
    }

    // Backdrop 생성 - GlassBottomSheetContent용 (Layer Backdrop)
    val backdrop = rememberLayerBackdrop()

    // isSwipedDown이 변경되면 swipeProgress도 동기화
    LaunchedEffect(isSwipedDown) {
        if (isSwipedDown) {
            swipeProgress = 1f // NewActivity로 전환 시 완전히 확장
        } else {
            swipeProgress = 0f // 복귀 시 원래 상태로
        }
    }

    // 진행률 업데이트 콜백
    val onSwipeProgressChange: (Float) -> Unit = { progress ->
        swipeProgress = progress
    }

    // 전체 화면 Box - pointerInput 제거 (GlassBottomSheetContent에서만 감지)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent) // 투명 배경
    ) {
        // 배경에 몬드리안 스타일 패턴 적용 (앱 처음 열 때만 랜덤 생성)
        // layerBackdrop을 적용하여 배경을 backdrop으로 캡처
        // 중요: backdrop-library.mdc 규칙에 따라 Canvas Composable 사용
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop) // 배경을 backdrop으로 캡처 (렌즈 효과를 위해 필수)
        ) {
            // 앱을 처음 열 때만 랜덤 패턴 생성 (remember로 고정)
            val mondrianData = remember {
                val random = kotlin.random.Random

                // 파스텔톤 브라운 계열 5개 색상 + RGB(205, 209, 210)
                val pastelBrownColors = listOf(
                    // 새로운 연습용 파스텔 팔레트
                    Color(0xFFF9F3E7), // 시각적 편안함을 주는 베이스 (Main Background)
                    Color(0xFFDDE6D5), // 차분한 집중력을 돕는 그린 (Soft Focus)
                    Color(0xFFC8E3D4), // 눈의 피로를 덜어주는 민트 (Eye Relief)
                    Color(0xFFB4D4FF), // 긴장을 완화하는 블루 (Calm Down)
                    Color(0xFFFDEFD9), // 기분 전환을 돕는 피치 (Mood Boost)
                    Color(0xFFE3E8E9)  // 정갈한 마무리를 돕는 그레이 (Clear Mind)
                )

                // 랜덤한 수직선과 수평선 개수 (3-7개)
                val verticalLines = (3..7).random()
                val horizontalLines = (3..7).random()

                // 수직선 위치 생성 (0~1 범위로 정규화하여 저장)
                val vLines = mutableListOf<Float>()
                repeat(verticalLines) {
                    vLines.add(random.nextFloat())
                }
                vLines.sort()

                // 수평선 위치 생성 (0~1 범위로 정규화하여 저장)
                val hLines = mutableListOf<Float>()
                repeat(horizontalLines) {
                    hLines.add(random.nextFloat())
                }
                hLines.sort()

                // 각 영역의 색상 미리 결정
                val regionColors = mutableListOf<Color>()
                for (i in 0..vLines.size) {
                    for (j in 0..hLines.size) {
                        val color = if (random.nextFloat() < 0.3f) {
                            pastelBrownColors.random()
                        } else {
                            Color.White
                        }
                        regionColors.add(color)
                    }
                }

                Triple(vLines, hLines, regionColors)
            }

            Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                val width = size.width
                val height = size.height

                // 정규화된 선 위치를 실제 화면 크기로 변환
                val vLines = listOf(0f) + mondrianData.first.map { it * width } + listOf(width)
                val hLines = listOf(0f) + mondrianData.second.map { it * height } + listOf(height)

                // 검정 선 그리기
                val lineWidth = 0.dp.toPx()

                // 수직선 그리기
                vLines.forEach { x ->
                    drawRect(
                        color = Color.Black,
                        topLeft = androidx.compose.ui.geometry.Offset(x - lineWidth / 2, 0f),
                        size = androidx.compose.ui.geometry.Size(lineWidth, height)
                    )
                }

                // 수평선 그리기
                hLines.forEach { y ->
                    drawRect(
                        color = Color.Black,
                        topLeft = androidx.compose.ui.geometry.Offset(0f, y - lineWidth / 2),
                        size = androidx.compose.ui.geometry.Size(width, lineWidth)
                    )
                }

                // 각 영역에 색상 채우기
                var colorIndex = 0
                for (i in 0 until vLines.size - 1) {
                    for (j in 0 until hLines.size - 1) {
                        val x = vLines[i] + lineWidth / 2
                        val y = hLines[j] + lineWidth / 2
                        val rectWidth = vLines[i + 1] - vLines[i] - lineWidth
                        val rectHeight = hLines[j + 1] - hLines[j] - lineWidth

                        val color = mondrianData.third.getOrNull(colorIndex++) ?: Color.White

                        drawRect(
                            color = color,
                            topLeft = androidx.compose.ui.geometry.Offset(x, y),
                            size = androidx.compose.ui.geometry.Size(rectWidth, rectHeight)
                        )
                    }
                }
            }
        }

        // GlassBottomSheet - 로딩 텍스트와 스와이프 힌트 모두 글래스 효과 적용
        // showLoadingText가 true이면 DOWNLOADING, isSwipeEnabled가 true이면 READY 상태
        val downloadState = when {
            isSwipeEnabled -> DownloadState.READY
            showLoadingText -> DownloadState.DOWNLOADING
            else -> DownloadState.DOWNLOADING // 기본값
        }

        GlassBottomSheetContent(
            backdrop = backdrop,
            downloadState = downloadState,
            alpha = if (showLoadingText || currentAlpha > 0.01f) 1f else 0f, // 로딩 중이거나 스와이프 힌트가 보일 때 표시
            isSwipedDown = isSwipedDown,
            swipeProgress = swipeProgress, // 진행률 전달
            onSwipeDown = onSwipeDown, // 콜백 전달
            onSwipeProgressChange = onSwipeProgressChange, // 진행률 업데이트 콜백
            isSwipeEnabled = isSwipeEnabled, // 활성화 여부 전달
            screenHeightPx = screenHeightPx // 화면 높이 전달
        )

        // 하단 네비게이션바 (조건부 렌더링)
        if (showBottomNav) {
            val context = LocalContext.current
            val isDownloading = showLoadingText || !isSwipeEnabled
            BottomNavigationBar(
                backdrop = backdrop,
                onBackPressed = {
                    if (context is AppCompatActivity) {
                        context.onBackPressedDispatcher.onBackPressed()
                    }
                },
                isDownloading = isDownloading,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
fun BoxScope.GlassBottomSheetContent(
    backdrop: Backdrop,
    downloadState: DownloadState,
    alpha: Float,
    isSwipedDown: Boolean = false,
    swipeProgress: Float = 0f, // 스와이프 진행률 (0.0 ~ 1.0)
    onSwipeDown: () -> Unit = {}, // 스와이프 완료 콜백
    onSwipeProgressChange: (Float) -> Unit = {}, // 진행률 업데이트 콜백
    isSwipeEnabled: Boolean = false, // 스와이프 활성화 여부
    screenHeightPx: Float = 0f // 화면 높이
) {
    if (alpha <= 0.01f) return

    val density = LocalDensity.current

    // GlassPlaygroundContent.kt 로직: 렌즈 효과 파라미터를 상태로 관리
    var cornerRadiusFrac by remember { mutableFloatStateOf(0.5f) }
    var blurRadiusDp by remember { mutableFloatStateOf(2.53f) }
    var refractionHeightFrac by remember { mutableFloatStateOf(0.2f) }
    var refractionAmountFrac by remember { mutableFloatStateOf(1.0f) }
    var chromaticAberration by remember { mutableFloatStateOf(1f) }

    // 현재 진행률을 remember로 저장하여 pointerInput에서 참조
    val currentProgress = remember { mutableFloatStateOf(swipeProgress) }

    // swipeProgress가 변경되면 currentProgress도 업데이트
    LaunchedEffect(swipeProgress) {
        currentProgress.value = swipeProgress
    }

    // 글래스의 초기 크기 (120.dp)를 화면 높이에 대한 비율로 계산
    val initialGlassHeightDp = 120.dp
    val initialProgressRatio = with(density) {
        (initialGlassHeightDp.toPx() / screenHeightPx).coerceIn(0f, 1f)
    }

    // 790-791줄 수정
    val bottomNavHeight = 100.dp // 하단 네비게이션바 높이
    val maxHeightRatio = with(density) {
        ((screenHeightPx - bottomNavHeight.toPx()) / screenHeightPx).coerceIn(0f, 1f)
    }
    val finalProgress = if (isSwipedDown) maxHeightRatio else (swipeProgress * maxHeightRatio)

    // 정렬 변경 제거 - 항상 상단 정렬로 유지하고 높이만 조절하여 글래스가 위로 올라가지 않도록 함

    // 가로는 match, 세로는 wrap 스타일로 유지
    Box(
        Modifier
            .align(Alignment.TopCenter) // 항상 상단 정렬로 고정
            .fillMaxWidth() // 가로는 match
            .then(
                if (finalProgress > 0f) {
                    Modifier.fillMaxHeight(finalProgress.coerceIn(0f, 1f))
                } else {
                    Modifier.wrapContentHeight()
                }
            )
            .padding(
                top = 50.dp,
                start = 28.dp,
                end = 28.dp,
//                bottom = 16.dp
//                top = 50.dp, // 항상 상단 패딩 유지
//                bottom = 0.dp
            )
    ) {
        Column(
            Modifier
                .fillMaxWidth() // 가로는 match
                .then(
                    if (finalProgress > 0f) {
                        Modifier.fillMaxHeight()
                    } else {
                        Modifier.heightIn(min = 120.dp, max = 120.dp)
                    }
                )
                .alpha(alpha)
                // GlassBottomSheetContent 내부 Column에서만 스와이프 제스처 감지
                // swipeProgress를 키로 사용하지 않아서 재실행 방지
                .pointerInput(isSwipeEnabled, screenHeightPx, initialProgressRatio) {
                    if (!isSwipeEnabled) return@pointerInput

                    var totalDragY = 0f
                    var initialProgress = 0f
                    var isDragging = false

                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            // 드래그 시작 시 글래스의 초기 크기(120.dp)를 기준으로 시작
                            // initialProgressRatio는 글래스 초기 높이를 화면 높이로 나눈 비율
                            initialProgress = initialProgressRatio
                            totalDragY = 0f // 드래그 시작 시 0부터 시작 (상대적 변화량)
                            isDragging = true
                        },
                        onDragEnd = {
                            // 초기 진행률 + 드래그로 인한 변화량으로 최종 진행률 계산
                            val finalProgress =
                                ((initialProgress * screenHeightPx + totalDragY) / screenHeightPx).coerceIn(
                                    0f,
                                    1f
                                )
                            // 스와이프 취소: 화면 높이의 30% 미만이면 원래대로
                            if (finalProgress < 0.3f) {
                                onSwipeProgressChange(0f)
                            } else {
                                // 화면 높이의 30% 이상이면 완료 처리
                                onSwipeDown()
                            }
                            isDragging = false
                            totalDragY = 0f
                            initialProgress = 0f
                        },
                        onVerticalDrag = { change, dragAmount ->
                            // 아래로 스와이프 (양수 dragAmount) 또는 위로 스와이프 (음수 dragAmount)
                            totalDragY += dragAmount
                            // 진행률 계산 (0.0 ~ 1.0) - 초기 진행률 + 드래그 변화량
                            val newProgress =
                                ((initialProgress * screenHeightPx + totalDragY) / screenHeightPx).coerceIn(
                                    0f,
                                    1f
                                )
                            onSwipeProgressChange(newProgress)
                        }
                    )
                }
                // GlassPlaygroundContent.kt와 동일한 패턴: 상태 변수들을 직접 읽어서 observeReads가 추적
                // 중요: effects 블록 내에서 상태 변수를 직접 읽어야 observeReads가 추적함
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { ContinuousRoundedRectangle(256f.dp / 2f * cornerRadiusFrac) },
                    effects = {
                        val minDimension = size.minDimension
                        vibrancy()
                        blur(blurRadiusDp.dp.toPx())
                        lens(
                            refractionHeight = refractionHeightFrac * minDimension * 0.5f,
                            refractionAmount = refractionAmountFrac * minDimension,
                            depthEffect = true,
                            chromaticAberration = chromaticAberration > 0f
                        )
                    },
                    highlight = { Highlight.Plain }
                )
        ) {
            val sliderSwitch = false;
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 콘텐츠 전환은 Crossfade로 처리
                Crossfade(
                    targetState = downloadState,
                    label = "glass-content-transition",
                    animationSpec = tween(1500, easing = LinearOutSlowInEasing)
                ) { state ->
                    when (state) {
                        DownloadState.DOWNLOADING -> DownloadingContent(backdrop = backdrop)
                        DownloadState.READY -> SwipeToStartContent(
                            isSwipedDown = isSwipedDown,
                            swipeProgress = swipeProgress
                        )
                    }
                }

                // GlassPlaygroundContent.kt 구조: 슬라이더 패널을 조건부로 표시
                // 샘플 코드와 동일한 구조 - sheetBackdrop를 조건부 블록 내부에서 선언
                if (false) {
                    val sheetBackdrop = rememberLayerBackdrop()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .drawBackdrop(
                                backdrop = backdrop,
                                shape = { ContinuousRoundedRectangle(32f.dp) },
                                effects = {
                                    vibrancy()
                                    blur(4f.dp.toPx())
                                    if (Build.VERSION.SDK_INT >= 33) {
                                        lens(16f.dp.toPx(), 32f.dp.toPx())
                                    }
                                },
                                highlight = { Highlight.Plain },
                                exportedBackdrop = sheetBackdrop,
                                onDrawSurface = { drawRect(Color.White.copy(alpha = 0.5f)) }
                            )
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Corner radius", color = Color.Black)
                                Text(
                                    "${String.format("%.3f", cornerRadiusFrac)}",
                                    color = Color.Black
                                )
                            }
                            if (sliderSwitch)
                                Slider(
                                    value = cornerRadiusFrac,
                                    onValueChange = { cornerRadiusFrac = it },
                                    valueRange = 0f..1f
                                )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Blur radius", color = Color.Black)
                                Text("${String.format("%.2f", blurRadiusDp)}", color = Color.Black)
                            }
                            if (sliderSwitch)
                                Slider(
                                    value = blurRadiusDp,
                                    onValueChange = { blurRadiusDp = it },
                                    valueRange = 0f..32f
                                )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Refraction height", color = Color.Black)
                                Text(
                                    "${String.format("%.3f", refractionHeightFrac)}",
                                    color = Color.Black
                                )
                            }
                            if (sliderSwitch)
                                Slider(
                                    value = refractionHeightFrac,
                                    onValueChange = { refractionHeightFrac = it },
                                    valueRange = 0f..1f
                                )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Refraction amount", color = Color.Black)
                                Text(
                                    "${String.format("%.3f", refractionAmountFrac)}",
                                    color = Color.Black
                                )
                            }
                            if (sliderSwitch)
                                Slider(
                                    value = refractionAmountFrac,
                                    onValueChange = { refractionAmountFrac = it },
                                    valueRange = 0f..1f
                                )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Chromatic aberration", color = Color.Black)
                                Text(
                                    "${String.format("%.3f", chromaticAberration)}",
                                    color = Color.Black
                                )
                            }
                            if (sliderSwitch)
                                Slider(
                                    value = chromaticAberration,
                                    onValueChange = { chromaticAberration = it },
                                    valueRange = 0f..1f
                                )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DownloadingContent(backdrop: Backdrop) {
    // 진행률 상태: 0f에서 시작 (0% ~ 1f = 100%)
    var targetProgress by remember { mutableStateOf(0f) }

    // 진행률을 0%에서 100%까지 애니메이션
    val progress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(
            durationMillis = 300, // 진행률 업데이트 시 애니메이션 속도 (빠른 반응)
            easing = LinearOutSlowInEasing
        ),
        label = "progress"
    )

    // ============================================
    // 🔥 DB 데이터 로딩 및 세팅 코드 작성 위치
    // ============================================
    // 여기서 DB에서 데이터를 불러오고 세팅하는 작업을 수행하세요.
    // 진행률에 따라 targetProgress를 업데이트하면 프로그레스 바가 자동으로 움직입니다.
    //
    // 예시:
    // LaunchedEffect(Unit) {
    //     // 1. DB 초기화 (0% ~ 20%)
    //     targetProgress = 0.2f
    //     initializeDatabase()
    //
    //     // 2. 데이터 로딩 (20% ~ 80%)
    //     val data = loadDataFromDatabase()
    //     targetProgress = 0.8f
    //
    //     // 3. 데이터 세팅 (80% ~ 100%)
    //     setupData(data)
    //     targetProgress = 1f
    //
    //     // 4. 로딩 완료 후 다음 단계로 진행
    //     // (예: showSwipeToStartHint() 호출 또는 상태 변경)
    // }
    //
    // 또는 코루틴을 사용한 예시:
    // LaunchedEffect(Unit) {
    //     coroutineScope {
    //         // DB 작업을 여러 단계로 나누어 진행률 업데이트
    //         launch {
    //             targetProgress = 0.1f
    //             val db = getDatabase()
    //             
    //             targetProgress = 0.3f
    //             val userData = db.loadUserData()
    //             
    //             targetProgress = 0.6f
    //             val settings = db.loadSettings()
    //             
    //             targetProgress = 0.9f
    //             setupApplication(userData, settings)
    //             
    //             targetProgress = 1f
    //             // 로딩 완료 - 다음 단계로 진행
    //         }
    //     }
    // }
    // ============================================

    // 임시: 컴포저블이 처음 생성될 때 100%로 애니메이션 시작 (테스트용)
    // 실제 DB 로딩 코드를 작성하면 이 부분을 제거하세요
    LaunchedEffect(Unit) {
        // TODO: 실제 DB 로딩 코드로 교체
        targetProgress = 1f
    }

    Column(
        modifier = Modifier
            .fillMaxWidth() // 가로는 match
//            .wrapContentHeight() // 세로는 wrap
            .wrapContentHeight()
            .heightIn(min = 120.dp, max = 120.dp) // 세로는 wrap
            .padding(vertical = 20.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "데이터를 불러오고 있어요",
            color = Color(0xFF2F4F4F),
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // GlassProgressBar 사용
        GlassProgressBar(
            backdrop = backdrop,
            progress = progress,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun GlassProgressBar(
    backdrop: Backdrop,
    progress: Float, // 0f ~ 1f
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(6.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        val trackBackdrop = rememberLayerBackdrop()

        // track (전체 진행 바 배경) - 글래스 효과 추가
        Box(
            Modifier
                .layerBackdrop(trackBackdrop)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { CircleShape },
                    effects = {
                        // 예제 규칙: vibrancy → blur → lens 순서
                        vibrancy()
                        blur(4.dp.toPx())
                        if (Build.VERSION.SDK_INT >= 33) {
                            lens(
                                refractionHeight = 3.dp.toPx(),
                                refractionAmount = 8.dp.toPx(),
                                depthEffect = true
                            )
                        }
                    }
                )
                .height(6.dp)
                .fillMaxWidth()
        )

        // progress fill (진행된 부분) - 글래스 효과 추가
        Box(
            Modifier
                .width((maxWidth * progress.coerceIn(0f, 1f))) // 진행률만큼만 채움
                .height(6.dp)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { CircleShape },
                    effects = {
                        // 예제 규칙: vibrancy → blur → lens 순서
                        vibrancy()
                        blur(4.dp.toPx())
                        if (Build.VERSION.SDK_INT >= 33) {
                            lens(
                                refractionHeight = 3.dp.toPx(),
                                refractionAmount = 8.dp.toPx(),
                                depthEffect = true
                            )
                        }
                    }
                )
        )

        // thumb (진행률 표시 원) - 글래스 효과 강화
        Box(
            Modifier
                .offset(x = (maxWidth - 28.dp) * progress.coerceIn(0f, 1f)) // 진행률에 따라 위치 조정
                .drawBackdrop(
                    backdrop = trackBackdrop,
                    shape = { CircleShape },
                    effects = {
                        // 예제 규칙: vibrancy → blur → lens 순서
                        vibrancy()
                        blur(8.dp.toPx())
                        if (Build.VERSION.SDK_INT >= 33) {
                            lens(
                                refractionHeight = 12.dp.toPx(),
                                refractionAmount = 16.dp.toPx(),
                                chromaticAberration = true
                            )
                        }
                    }
                )
                .size(28.dp, 28.dp)
        )
    }
}

@Composable
fun SwipeToStartContent(
    isSwipedDown: Boolean = false,
    swipeProgress: Float = 0f
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current

    // 스와이프 진행률에 따라 상단 패딩 증가 (텍스트가 아래로 이동)
    val screenHeightDp = configuration.screenHeightDp.dp
    // 기본 20.dp + 진행률에 따라 추가 이동 (최대 화면 높이의 30% 정도)
    val topPadding: androidx.compose.ui.unit.Dp =
        20.dp + (screenHeightDp.value * 0.3f * swipeProgress).dp

    Column(
        modifier = Modifier
            .fillMaxWidth() // 가로는 match
            .wrapContentHeight() // 세로는 wrap
            .padding(
                top = topPadding,
                bottom = 20.dp,
                start = 20.dp, // horizontal 대신 start 사용
                end = 20.dp    // horizontal 대신 end 사용
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 텍스트와 드롭렛을 AnimatedVisibility로 감싸서 숨김/표시
        AnimatedVisibility(
            visible = !isSwipedDown && swipeProgress < 0.9f, // 90% 이상이면 숨김
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "아래로 스와이프하고 시작하기",
                    color = Color(0xFF2F4F4F),
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 10.dp)
                )

                DropletIndicator()
            }
        }
    }
}

/**
 * 하단 네비게이션바 컴포저블
 */
@Composable
fun BottomNavigationBar(
    backdrop: Backdrop,
    onBackPressed: () -> Unit = {},
    isDownloading: Boolean = false,
    modifier: Modifier = Modifier
) {
    val isLightTheme = !isSystemInDarkTheme()
    val contentColor = if (isLightTheme) Color.Black else Color.White

    var selectedTabIndex by remember { mutableIntStateOf(0) } // 기본값: 홈

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isDownloading) {
                        Modifier.alpha(0.5f) // 투명도 조절로 비활성화 표시
                    } else {
                        Modifier
                    }
                ),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // LiquidBottomTabs 그룹 (뒤로가기 제외)
            LiquidBottomTabs(
                selectedTabIndex = { selectedTabIndex },
                onTabSelected = { index ->
                    if (!isDownloading) {
                        selectedTabIndex = index
                        when (index) {
                            0 -> {
                                // 홈
                            }

                            1 -> {
                                // 뉴스레터
                            }

                            2 -> {
                                // 마이페이지
                            }
                        }
                    }
                },
                backdrop = backdrop,
                tabsCount = 3,
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (isDownloading) {
                            Modifier.alpha(0.5f)
                        } else {
                            Modifier
                        }
                    )
            ) {
                // 1. 홈
                LiquidBottomTab({ selectedTabIndex = 0 }) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = "홈으로 진입",
                        modifier = Modifier.size(28f.dp),
                        tint = contentColor
                    )
//                    BasicText(
//                        "홈",
//                        style = TextStyle(contentColor, 12f.sp)
//                    )
                }

                // 2. 뉴스레터 탭
                LiquidBottomTab({ selectedTabIndex = 1 }) {
                    Icon(
                        imageVector = Icons.Default.Article,
                        contentDescription = "뉴스레터",
                        modifier = Modifier.size(28f.dp),
                        tint = contentColor
                    )
//                    BasicText(
//                        "뉴스레터",
//                        style = TextStyle(contentColor, 12f.sp)
//                    )
                }

                // 3. 마이페이지
                LiquidBottomTab({ selectedTabIndex = 2 }) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = "마이페이지 진입",
                        modifier = Modifier.size(28f.dp),
                        tint = contentColor
                    )
//                    BasicText(
//                        "마이페이지",
//                        style = TextStyle(contentColor, 12f.sp)
//                    )
                }
            }

            // 분리된 뒤로가기 버튼
            BackButton(
                backdrop = backdrop,
                onBackPressed = if (!isDownloading) {
                    onBackPressed
                } else {
                    { /* 다운로드 중에는 클릭 방지 */ }
                },
                contentColor = contentColor,
                modifier = if (isDownloading) {
                    Modifier.alpha(0.5f)
                } else {
                    Modifier
                }
            )
        }
    }
}

/**
 * 분리된 뒤로가기 버튼 컴포저블
 */
@Composable
fun BackButton(
    backdrop: Backdrop,
    onBackPressed: () -> Unit,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    val isLightTheme = !isSystemInDarkTheme()
    val containerColor =
        if (isLightTheme) Color(0xFFFAFAFA).copy(0.4f)
        else Color(0xFF121212).copy(0.4f)

    LiquidButton(
        onClick = onBackPressed,
        backdrop = backdrop,
        modifier = modifier.height(64.dp),
        surfaceColor = containerColor
    ) {
        Icon(
            imageVector = Icons.Default.ArrowBack,
            contentDescription = "뒤로",
            modifier = Modifier.size(28f.dp),
            tint = contentColor
        )
//        BasicText(
//            "",
//            style = TextStyle(contentColor, 12f.sp)
//        )
    }
}
