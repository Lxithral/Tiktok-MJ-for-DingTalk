package com.lxithral.mjegg.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
 * 打包 MiSans 静态子集字重 —— 老系统(如 Android 11 只有小米兰亭 Pro)与 HyperOS 观感一致。
 * HyperOS 正文实为 Medium 字重, VF 默认实例(400)显细, 而 Compose 的
 * Font(variationSettings) 在部分机型实测不生效 —— 所以离线把 VF 实例化成
 * 500/700 静态 TTF 并按 App 文案子集化(生成脚本: app/mobile/fonts-src/make_misans.py,
 * 重新生成时机: 界面新增文案出现"豆腐块"时)。子集外的字符由平台回落系统字体。
 */
private val miSans = FontFamily(
    Font(R.font.misans_500, weight = FontWeight.Normal),
    Font(R.font.misans_500, weight = FontWeight.Medium),
    Font(R.font.misans_500, weight = FontWeight.SemiBold),
    Font(R.font.misans_700, weight = FontWeight.Bold),
    Font(R.font.misans_700, weight = FontWeight.Black),
)

private fun TextStyle.withFamily(family: FontFamily): TextStyle = copy(fontFamily = family)

private val appTextStyles: TextStyles by lazy { buildAppTextStyles(miSans) }

private fun buildAppTextStyles(family: FontFamily): TextStyles {
    val b = defaultTextStyles()
    return b.copy(
        main = b.main.withFamily(family),
        paragraph = b.paragraph.withFamily(family),
        body1 = b.body1.withFamily(family),
        body2 = b.body2.withFamily(family),
        button = b.button.withFamily(family),
        footnote1 = b.footnote1.withFamily(family),
        footnote2 = b.footnote2.withFamily(family),
        headline1 = b.headline1.withFamily(family),
        headline2 = b.headline2.withFamily(family),
        subtitle = b.subtitle.withFamily(family),
        title1 = b.title1.withFamily(family),
        title2 = b.title2.withFamily(family),
        title3 = b.title3.withFamily(family),
        title4 = b.title4.withFamily(family),
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

    // 打包 MiSans 全局文字(miuix 文字系统入口, 见 miSans 注释)
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
