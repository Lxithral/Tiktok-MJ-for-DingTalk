package com.lxithral.mjegg.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.lxithral.mjegg.platform.ColorMode
import com.lxithral.mjegg.platform.SettingsStore
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

/**
 * 全局主题入口 —— 唯一一处决定"现在该用什么配色"。
 *
 * 支持: 深色四态(SYSTEM/LIGHT/DARK/AMOLED) × Monet 开关 × 15 色预设。
 * AMOLED 用纯黑覆盖 background/surface 系列, 其余沿用 miuix 深色方案。
 */
@Composable
fun AppTheme(settings: SettingsStore, content: @Composable () -> Unit) {
    val isDark = resolveIsDark(settings.colorMode)

    val mode = when {
        settings.monet -> when (settings.colorMode) {
            ColorMode.SYSTEM -> ColorSchemeMode.MonetSystem
            ColorMode.LIGHT -> ColorSchemeMode.MonetLight
            ColorMode.DARK, ColorMode.AMOLED -> ColorSchemeMode.MonetDark
        }

        settings.colorMode == ColorMode.SYSTEM -> ColorSchemeMode.System
        settings.colorMode == ColorMode.LIGHT -> ColorSchemeMode.Light
        else -> ColorSchemeMode.Dark
    }

    val light = remember { lightColorScheme() }
    val dark = remember(settings.colorMode) {
        if (settings.colorMode == ColorMode.AMOLED) {
            darkColorScheme(
                background = Color.Black,
                surface = Color.Black,
                surfaceVariant = Color.Black,
                surfaceContainer = Color(0xFF0A0A0A),
                surfaceContainerHigh = Color(0xFF0A0A0A),
                surfaceContainerHighest = Color(0xFF121212),
            )
        } else {
            darkColorScheme()
        }
    }

    // keyColor == 0 表示交给系统壁纸取色(null), 否则用预设色当种子
    val seed = remember(settings.keyColor) {
        if (settings.keyColor == 0) null else Color(settings.keyColor)
    }

    val controller = remember(mode, seed, light, dark, isDark) {
        ThemeController(
            colorSchemeMode = mode,
            lightColors = light,
            darkColors = dark,
            keyColor = seed,
            isDark = isDark,
        )
    }

    MiuixTheme(controller = controller, content = content)
}

/** 深浅色判定收敛到这一个函数, 全 App 只此一处。 */
@Composable
fun resolveIsDark(mode: ColorMode): Boolean = when (mode) {
    ColorMode.SYSTEM -> isSystemInDarkTheme()
    ColorMode.LIGHT -> false
    ColorMode.DARK, ColorMode.AMOLED -> true
}
