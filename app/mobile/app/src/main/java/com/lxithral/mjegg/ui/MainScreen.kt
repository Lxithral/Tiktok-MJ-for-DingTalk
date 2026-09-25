package com.lxithral.mjegg.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.lxithral.mjegg.platform.SettingsStore
import com.lxithral.mjegg.ui.navigation.BottomBar
import com.lxithral.mjegg.ui.navigation.LocalNavigator
import com.lxithral.mjegg.ui.navigation.Route
import com.lxithral.mjegg.ui.screen.feature.FeatureScreen
import com.lxithral.mjegg.ui.screen.home.HomeScreen
import com.lxithral.mjegg.ui.screen.module.ModuleScreen
import com.lxithral.mjegg.ui.screen.settings.SettingsScreen
import com.lxithral.mjegg.ui.theme.resolveIsDark
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop

/**
 * 主界面：miuix Scaffold + HorizontalPager（4 个一级 Tab）+ 三形态底栏。
 *
 * 重要边界：HorizontalPager 只负责一级 Tab；主题/诊断/关于等二级页全部交给
 * 外层 miuix-nav 返回栈，不能再用布尔变量在这里模拟页面跳转。
 */
@Composable
fun MainScreen(settings: SettingsStore) {
    val pagerState = rememberPagerState(pageCount = { 4 })
    val scope = rememberCoroutineScope()
    val backdrop = rememberLayerBackdrop()
    val isDark = resolveIsDark(settings.colorMode)
    val navigator = LocalNavigator.current
    val themeKey = remember(settings.colorMode, settings.monet, settings.keyColor) {
        "${settings.colorMode}-${settings.monet}-${settings.keyColor}"
    }

    // 主页内返回：先回到第 0 个 Tab；已经在第 0 页才交给 NavDisplay/系统退出。
    BackHandler(enabled = pagerState.currentPage != 0) {
        scope.launch { pagerState.animateScrollToPage(0) }
    }

    Scaffold(
        bottomBar = {
            BottomBar(
                settings = settings,
                pagerState = pagerState,
                backdrop = backdrop,
                isDark = isDark,
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .layerBackdrop(backdrop)
        ) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                when (page) {
                    0 -> HomeScreen(settings, isDark, themeKey)
                    1 -> FeatureScreen(settings)
                    2 -> ModuleScreen(settings)
                    else -> SettingsScreen(
                        settings = settings,
                        isDark = isDark,
                        onOpenTheme = { navigator.push(Route.ThemeSettings) },
                        onOpenDiagnostics = { navigator.push(Route.Diagnostics) },
                        onOpenAbout = { navigator.push(Route.About) },
                    )
                }
            }
        }
    }
}
