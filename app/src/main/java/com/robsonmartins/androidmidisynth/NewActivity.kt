package com.robsonmartins.androidmidisynth

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.shadow.Shadow
import com.kyant.capsule.ContinuousRoundedRectangle
import com.robsonmartins.androidmidisynth.components.LiquidButton
import com.robsonmartins.androidmidisynth.dto.CantamonyAlbum
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.ScatterPlot
import androidx.compose.material.icons.filled.Speed
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight

class NewActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_USER_NAME = "EXTRA_USER_NAME"
        const val EXTRA_USER_HANDLE = "EXTRA_USER_HANDLE"
    }


    private lateinit var composeView: ComposeView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Window 투명도 설정 (SplashActivity와 동일)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )
        }
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = false
        controller.hide(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        supportActionBar?.hide()

        // ComposeView로 전환
        composeView = ComposeView(this)
        composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        composeView.setContent {
            NewActivityContent(
                onActivityClick = { activityName ->
                    handleMenuItemClick(activityName)
                },
                onCloseClick = {
                    finish()
                }
            )
        }
        setContentView(composeView)
    }

    private fun handleMenuItemClick(activityName: String) {
        when (activityName) {
            "SelectMidiActivity" -> {
                startActivity(Intent(this, SelectMidiActivity::class.java))
            }
            "ProfileActivity" -> {
                startActivity(Intent(this, ProfileActivity::class.java))
            }
            "RoomActivity" -> {
                val intent = Intent(this, RoomActivity::class.java).apply {
                    putExtra(RoomActivity.EXTRA_ROOM_ID, "room_default")
                    putExtra(RoomActivity.EXTRA_ROOM_TITLE, "소규모 그룹방 A")
                }
                startActivity(intent)
            }
            "MainActivity" -> {
                val demoAlbum = CantamonyAlbum(
                    albumName = "Dorico 학습앨범 5",
                    albumAsset = mapOf(
                        "musicxml" to "Minuet 도리코 최종 - 01_피아노 - 01 Minuet.musicxml",
                        "mid" to "j - Full score - Flow 1.mid",
                    )
                )
                val intent = Intent(this, MainActivity::class.java).apply {
                    putExtra(MainActivity.EXTRA_SELECTED_ALBUM, demoAlbum)
                    putExtra(MainActivity.EXTRA_MUSICXML_FILE_PATH, demoAlbum.albumAsset["musicxml"])
                }
                startActivity(intent)
            }
        }
    }

    override fun finish() {
        super.finish()
        // Close(또는 뒤로가기) 시 현재 액티비티가 위로 올라가며 닫히는 효과
        try {
            overridePendingTransition(0, R.anim.slide_out_to_top)
        } catch (e: Exception) {
            // Window가 이미 파괴된 경우 무시
        }
    }
}

@Composable
fun NewActivityContent(
    onActivityClick: (String) -> Unit,
    onCloseClick: () -> Unit
) {
    val backdrop = rememberLayerBackdrop()
    val context = LocalContext.current
    val isLightTheme = !isSystemInDarkTheme()
    val contentColor = if (isLightTheme) Color.Black else Color.White
    
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    
    // 실제 화면 높이를 가져옴
    val screenHeightPx = with(density) { 
        configuration.screenHeightDp.dp.toPx() 
    }

    var cornerRadiusFrac by remember { mutableFloatStateOf(0.5f) }
    var blurRadiusDp by remember { mutableFloatStateOf(2.53f) }
    var refractionHeightFrac by remember { mutableFloatStateOf(0.2f) }
    var refractionAmountFrac by remember { mutableFloatStateOf(1.0f) }
    var chromaticAberration by remember { mutableFloatStateOf(10f) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        // 배경을 backdrop으로 캡처 (이미지 포함)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
                .padding(top = 80.dp, end = 20.dp)
        ) {
            // 이미지를 backdrop 캡처 영역 안에 배치
            Image(
                painter = painterResource(id = R.drawable.cantamony1),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.TopEnd) // 상단 오른쪽 정렬
                    .height(200.dp)
                    .width(200.dp),
                contentScale = ContentScale.Fit
            )
        }

        // ScrollContainerContent 스타일로 재구성
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 42.dp)
                .systemBarsPadding()
                .displayCutoutPadding()
        ) {
            // 상단 여백도 화면 크기에 비례하여 계산
            val topSpacingRatio = 0.05f // 화면 높이의 5% (필요에 따라 조정)
            val topSpacing = with(density) {
                (screenHeightPx * topSpacingRatio).toDp()
            }
            Spacer(Modifier.height(topSpacing))

            // 1. 첫 번째 액티비티 (SelectMidiActivity)를 컨테이너에 등록
            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                // 화면 높이의 비율로 높이 설정
                val heightRatio = 0.1f // 50% 비율 (필요에 따라 조정 가능)
                val calculatedHeight = with(density) {
                    (screenHeightPx * heightRatio).toDp()
                }
                
                // 글래스 레이어 (이미지가 backdrop으로 캡처되어 굴절 효과 적용됨)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(calculatedHeight)
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { ContinuousRoundedRectangle(32.dp) },
                            effects = {
                                val minDimension = size.minDimension
                                vibrancy()
                                blur(1.dp.toPx())
                                if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    lens(
                                        refractionHeight = 70f,
                                        refractionAmount = 100f,
                                        depthEffect = true,
                                        chromaticAberration = chromaticAberration > 0f
                                    )
                                }
                            },
                            shadow = {
                                // 초록색 그림자 효과
                                Shadow(
                                    radius = 16.dp,
                                    offset = androidx.compose.ui.unit.DpOffset(0.dp, 4.dp),
                                    color = Color(0xFF00FF88), // 초록색
                                    alpha = 0.8f
                                )
                            },
                            onDrawSurface = {
                                // 글래스 효과를 위한 반투명 배경
                                drawRect(Color.White.copy(alpha = 0.1f))
                            }
                        )
                        .clickable(onClick = { onActivityClick("SelectMidiActivity") })
                ) {
                    // 앞에 배치할 콘텐츠
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.Start,
                        verticalArrangement = Arrangement.Center
                    ) {
                        BasicText(
                            "Your Vision, Our Symphony",
                            modifier = Modifier
                                .then(
                                    if (isLightTheme) {
                                        // plus darker
                                        Modifier
                                    } else {
                                        // plus lighter
                                        Modifier.graphicsLayer(blendMode = BlendMode.Plus)
                                    }
                                ),
                            style = TextStyle(contentColor.copy(0.68f), 15f.sp),
                            maxLines = 1
                        )

                        BasicText(
                            "Cantamony",
                            style = TextStyle(contentColor, 24f.sp, FontWeight.Medium)
                        )
                    }

                }
            }

            Spacer(Modifier.height(32.dp))

            // 2. 나머지 액티비티들을 아이콘 버튼으로 배치 (글래스 영역과 분리)
            // 한 줄에 4개씩 배치
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 50.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 첫 번째 줄 (4개)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ActivityIconButton(
                        icon = Icons.Filled.Event,
                        label = "이벤트",
                        backdrop = backdrop,
                        onClick = { onActivityClick("ProfileActivity") },
                        contentColor = contentColor,
                        modifier = Modifier.weight(1f)
                    )

                    ActivityIconButton(
                        icon = Icons.Filled.Speed,
                        label = "튜너",
                        backdrop = backdrop,
                        onClick = { onActivityClick("RoomActivity") },
                        contentColor = contentColor,
                        modifier = Modifier.weight(1f)
                    )

                    ActivityIconButton(
                        icon = Icons.Default.ScatterPlot,
                        label = "메트로놈",
                        backdrop = backdrop,
                        onClick = { onActivityClick("MainActivity") },
                        contentColor = contentColor,
                        modifier = Modifier.weight(1f)
                    )

                    ActivityIconButton(
                        icon = Icons.Default.Forum,
                        label = "커뮤니티",
                        backdrop = backdrop,
                        onClick = { onActivityClick("MainActivity") },
                        contentColor = contentColor,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 첫 번째 줄 (4개)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ActivityIconButton(
                        icon = Icons.Filled.Psychology,
                        label = "AI 분석",
                        backdrop = backdrop,
                        onClick = { onActivityClick("RoomActivity") },
                        contentColor = contentColor,
                        modifier = Modifier.weight(1f)
                    )
                    ActivityIconButton(
                        icon = Icons.Filled.Add,
                        label = "",
                        backdrop = backdrop,
                        onClick = { onActivityClick("RoomActivity") },
                        contentColor = contentColor,
                        modifier = Modifier.weight(1f)
                    )
                    ActivityIconButton(
                        icon = Icons.Filled.Add,
                        label = "",
                        backdrop = backdrop,
                        onClick = { onActivityClick("RoomActivity") },
                        contentColor = contentColor,
                        modifier = Modifier.weight(1f)
                    )
                    ActivityIconButton(
                        icon = Icons.Filled.Add,
                        label = "",
                        backdrop = backdrop,
                        onClick = { onActivityClick("RoomActivity") },
                        contentColor = contentColor,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(100.dp)) // 하단 네비게이션바 공간 확보
        }

        // 하단 네비게이션바 (SplashActivity와 동일)
        BottomNavigationBar(
            backdrop = backdrop,
            onBackPressed = {
                if (context is AppCompatActivity) {
                    context.onBackPressedDispatcher.onBackPressed()
                }
            },
            isDownloading = false,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

/**
 * 액티비티 아이콘 버튼 컴포저블
 */
@Composable
fun ActivityIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    backdrop: Backdrop,
    onClick: () -> Unit,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    val isLightTheme = !isSystemInDarkTheme()
    val containerColor =
        if (isLightTheme) Color(0xFFFAFAFA).copy(0.4f)
        else Color(0xFF121212).copy(0.4f)

    Box(
        modifier = modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { ContinuousRoundedRectangle(24.dp) },
                effects = {
                    vibrancy()
                    blur(4.dp.toPx())
                    if (android.os.Build.VERSION.SDK_INT >= 33) {
                        lens(12.dp.toPx(), 24.dp.toPx())
                    }
                }
            )
            .height(80.dp)
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.height(4.dp))
            BasicText(
                text = label,
                style = TextStyle(
                    color = contentColor,
                    fontSize = 12.sp
                )
            )
        }
    }
}

@Composable
fun CloseButton(
    backdrop: Backdrop,
    onCloseClick: () -> Unit,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    val isLightTheme = !isSystemInDarkTheme()
    val containerColor =
        if (isLightTheme) Color(0xFF6E748B)
        else Color(0xFF6E748B)

    LiquidButton(
        onClick = onCloseClick,
        backdrop = backdrop,
        modifier = modifier.height(56.dp),
        surfaceColor = containerColor
    ) {
        BasicText(
            text = "Close",
            style = TextStyle(
                color = Color.White,
                fontSize = 16.sp
            )
        )
    }
}
