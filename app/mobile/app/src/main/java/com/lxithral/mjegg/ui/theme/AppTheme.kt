package com.lxithral.mjegg.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.lxithral.mjegg.R
import com.lxithral.mjegg.platform.ColorMode
import com.lxithral.mjegg.platform.SettingsStore
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.TextStyles
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.defaultTextStyles
import top.yukonga.miuix.kmp.theme.lightColorScheme

/**
 * 打包 MiSans 可变字体 —— 老系统(如 Android 11 只有小米兰亭 Pro)与 HyperOS 观感一致。
 * 单 VF 实例(框架按 resId 缓存, 不重复加载); Bold 走系统合成加粗。
 */
private val miSans = FontFamily(Font(R.font.misans_vf))

private fun TextStyle.misans(): TextStyle = copy(fontFamily = miSans)

private val appTextStyles: TextStyles by lazy {
    val b = defaultTextStyles()
    b.copy(
        main = b.main.misans(),
        paragraph = b.paragraph.misans(),
        body1 = b.body1.misans(),
        body2 = b.body2.misans(),
        button = b.button.misans(),
        footnote1 = b.footnote1.misans(),
        footnote2 = b.footnote2.misans(),
        headline1 = b.headline1.misans(),
        headline2 = b.headline2.misans(),
        subtitle = b.subtitle.misans(),
        title1 = b.title1.misans(),
        title2 = b.title2.misans(),
        title3 = b.title3.misans(),
        title4 = b.title4.misans(),
    )
}

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

    val controller = remember(
        mode,
        seed,
        light,
        dark,
        isDark,
        settings.colorStyle,
        settings.colorSpec,
    ) {
        ThemeController(
            colorSchemeMode = mode,
            lightColors = light,
            darkColors = dark,
            keyColor = seed,
            colorSpec = settings.colorSpec,
            paletteStyle = settings.colorStyle,
            isDark = isDark,
        )
    }

    MiuixTheme(controller = controller, textStyles = appTextStyles, content = content)
}

/** 深浅色判定收敛到这一个函数, 全 App 只此一处。 */
@Composable
fun resolveIsDark(mode: ColorMode): Boolean = when (mode) {
    ColorMode.SYSTEM -> isSystemInDarkTheme()
    ColorMode.LIGHT -> false
    ColorMode.DARK, ColorMode.AMOLED -> true
}

/** KernelSU 液态玻璃底栏移植源码需要的统一深色判定入口。 */
@Composable
fun isInDarkTheme(): Boolean = resolveIsDark(SettingsStore.get(androidx.compose.ui.platform.LocalContext.current).colorMode)
