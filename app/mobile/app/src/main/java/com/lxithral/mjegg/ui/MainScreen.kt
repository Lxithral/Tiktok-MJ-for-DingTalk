package com.lxithral.mjegg.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.lxithral.mjegg.platform.SettingsStore
import com.lxithral.mjegg.ui.navigation.BottomBar
import com.lxithral.mjegg.ui.screen.feature.FeatureScreen
import com.lxithral.mjegg.ui.screen.home.HomeScreen
import com.lxithral.mjegg.ui.screen.module.ModuleScreen
import com.lxithral.mjegg.ui.screen.settings.DiagnosticsScreen
import com.lxithral.mjegg.ui.screen.settings.SettingsScreen
import com.lxithral.mjegg.ui.screen.settings.ThemeSettingsScreen
import com.lxithral.mjegg.ui.theme.resolveIsDark
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop

/**
 * 主界面: miuix Scaffold + HorizontalPager(4 页) + 三形态底栏。
 *
 * 内容层挂 layerBackdrop, 底栏(液态玻璃形态)才能采样并模糊下层内容。
 */
@Composable
fun MainScreen(settings: SettingsStore) {
    val pagerState = rememberPagerState(pageCount = { 4 })
    val backdrop = rememberLayerBackdrop()
    val isDark = resolveIsDark(settings.colorMode)

    // 二级页: 主题设置 / 诊断。用 rememberSaveable 保证旋屏后仍在原页
    var showTheme by rememberSaveable { mutableStateOf(false) }
    var showDiagnostics by rememberSaveable { mutableStateOf(false) }
    // 用递增 key 强制重建页面, 让"重开应用级"的设置(如 AMOLED)立即重绘
    val themeKey = remember(settings.colorMode, settings.monet, settings.keyColor) {
        "${settings.colorMode}-${settings.monet}-${settings.keyColor}"
    }

    if (showTheme) {
        ThemeSettingsScreen(settings = settings, onBack = { showTheme = false })
        return
    }
    if (showDiagnostics) {
        DiagnosticsScreen(settings = settings, onBack = { showDiagnostics = false })
        return
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
                        onOpenTheme = { showTheme = true },
                        onOpenDiagnostics = { showDiagnostics = true },
                    )
                }
            }
        }
    }
}
