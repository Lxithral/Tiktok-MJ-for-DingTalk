package com.lxithral.mjegg.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.lxithral.mjegg.platform.BottomBarStyle
import com.lxithral.mjegg.platform.SettingsStore
import com.lxithral.mjegg.ui.navigation.BottomBar
import com.lxithral.mjegg.ui.navigation.LocalNavigator
import com.lxithral.mjegg.ui.navigation.Route
import com.lxithral.mjegg.ui.screen.feature.FeatureScreen
import com.lxithral.mjegg.ui.screen.home.HomeScreen
import com.lxithral.mjegg.ui.screen.settings.SettingsScreen
import com.lxithral.mjegg.ui.theme.resolveIsDark
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 主界面：3 个一级 Tab（主页/功能/设置）+ 液态玻璃底栏。
 *
 * 玻璃采样（参照 ADBKit 的成熟实现，修"底栏下方一整块黑"）：
 * - `rememberLayerBackdrop { drawRect(surface); drawContent() }` **先铺一层 surface 底色**，
 *   页面内容没画到的区域（底栏后面）采样到的是 surface，而不是未定义的纯黑；
 * - 采样层挂全屏 Pager，页面内容用 contentPadding 避让栏，内容能延伸到栏后面，
 *   玻璃折射采到的就是真实内容；
 * - 单一外层 TopAppBar（标题随 Tab 切换），折叠行为由各页 LazyColumn 的 nestedScroll 驱动；
 * - `contentWindowInsets` 只吃导航栏 inset，状态栏 inset 由 TopAppBar 自己处理（否则标题偏低）。
 */
@Composable
fun MainScreen(settings: SettingsStore, onRerunOobe: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scrollBehavior = MiuixScrollBehavior()
    val scope = rememberCoroutineScope()
    val isDark = resolveIsDark(settings.colorMode)
    val navigator = LocalNavigator.current
    val surfaceColor = MiuixTheme.colorScheme.surface

    val liquidGlassActive = settings.bottomBarStyle == BottomBarStyle.LIQUID_GLASS &&
            settings.enableBlur && isRuntimeShaderSupported()

    val backdrop = rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }

    // 主页内返回：先回到第 0 个 Tab；已经在第 0 页才交给 NavDisplay/系统退出。
    // 只有本页是栈顶时才接管返回 —— 二级页(主题设置/诊断/关于)存活期间抢吃返回事件
    // 会导致全面屏返回手势失灵。
    BackHandler(enabled = pagerState.currentPage != 0 && navigator.backStackSize() == 1) {
        scope.launch { pagerState.animateScrollToPage(0) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = when (pagerState.currentPage) {
                    0 -> "MJ 彩蛋"
                    1 -> "功能"
                    else -> "设置"
                },
                largeTitle = when (pagerState.currentPage) {
                    0 -> "MJ 彩蛋"
                    1 -> "功能"
                    else -> "设置"
                },
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            BottomBar(
                settings = settings,
                pagerState = pagerState,
                backdrop = backdrop,
                isDark = isDark,
            )
        },
        contentWindowInsets = WindowInsets.navigationBars,
    ) { padding ->
        // §8.2 enter_home_anim 接力: OOBE 完成后首帧 scale 1.3→1.0(619ms 弹簧 damping0.65)
        // + alpha 0→1(230ms, startOffset 60, sine_in_out)
        val homeEnter = settings.pendingHomeEnterAnim
        if (homeEnter) settings.pendingHomeEnterAnim = false
        val homeScale = remember { androidx.compose.animation.core.Animatable(if (homeEnter) 1.3f else 1f) }
        val homeAlpha = remember { androidx.compose.animation.core.Animatable(if (homeEnter) 0f else 1f) }
        androidx.compose.runtime.LaunchedEffect(homeEnter) {
            if (!homeEnter) return@LaunchedEffect
            kotlinx.coroutines.coroutineScope {
                launch {
                    homeAlpha.animateTo(
                        1f,
                        androidx.compose.animation.core.tween(
                            230,
                            delayMillis = 60,
                            // enter_home_anim: sine_in_out = pathInterpolator(0.37, 0, 0.63, 1)
                            easing = androidx.compose.animation.core.CubicBezierEasing(0.37f, 0f, 0.63f, 1f),
                        ),
                    )
                }
                launch {
                    homeScale.animateTo(
                        1f,
                        androidx.compose.animation.core.spring(dampingRatio = 0.65f, stiffness = 130f),
                    )
                }
            }
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = homeScale.value
                    scaleY = homeScale.value
                    alpha = homeAlpha.value
                }
                .then(
                    if (liquidGlassActive) Modifier.layerBackdrop(backdrop) else Modifier
                ),
            beyondViewportPageCount = 1,
        ) { page ->
            when (page) {
                0 -> HomeScreen(settings, isDark, padding, scrollBehavior)
                1 -> FeatureScreen(settings, padding, scrollBehavior)
                else -> SettingsScreen(
                    settings = settings,
                    padding = padding,
                    scrollBehavior = scrollBehavior,
                    onOpenTheme = { navigator.push(Route.ThemeSettings) },
                    onOpenDiagnostics = { navigator.push(Route.Diagnostics) },
                    onOpenAbout = { navigator.push(Route.About) },
                    onRerunOobe = onRerunOobe,
                )
            }
        }
    }
}
