package com.lxithral.mjegg.ui.screen.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.lxithral.mjegg.platform.BottomBarStyle
import com.lxithral.mjegg.platform.ColorMode
import com.lxithral.mjegg.platform.SettingsStore
import com.lxithral.mjegg.ui.theme.ThemePalette
import com.lxithral.mjegg.ui.theme.paletteNameOf
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

/**
 * 主题设置二级页。
 *
 * 按套壳指南：滚动内容接 `nestedScroll` 让大标题自动折叠；
 * 分段选择用 miuix `TabRow`，枚举下拉用 `OverlayDropdownPreference`，
 * 开关/滑条用 `SwitchPreference` / `SliderPreference`，不自绘选择行。
 */
@Composable
fun ThemeSettingsScreen(settings: SettingsStore, onBack: () -> Unit) {
    val scrollBehavior = MiuixScrollBehavior()
    val blurSupported = isRuntimeShaderSupported()
    val colorTabs = listOf("跟随系统", "浅色", "深色")
    val selectedColorTab = when (settings.colorMode) {
        ColorMode.SYSTEM -> 0
        ColorMode.LIGHT -> 1
        ColorMode.DARK, ColorMode.AMOLED -> 2
    }
    val paletteItems = ThemePalette.map { it.name }
    val paletteIndex = ThemePalette.indexOfFirst { it.argb == settings.keyColor }.coerceAtLeast(0)
    val styleItems = ThemePaletteStyle.entries.map { it.displayName() }
    val styleIndex = ThemePaletteStyle.entries.indexOf(settings.colorStyle).coerceAtLeast(0)
    val specItems = listOf("SPEC_2021", "SPEC_2025")
    val specIndex = if (settings.colorSpec == ThemeColorSpec.Spec2025) 1 else 0
    val barItems = listOf("标准", "悬浮", "液态玻璃")
    val barIndex = settings.bottomBarStyle.ordinal

    Scaffold(
        topBar = {
            TopAppBar(
                title = "主题设置",
                largeTitle = "主题设置",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Back, contentDescription = "返回")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        ) {
            item {
                SmallTitle("深色模式")
                TabRow(
                    tabs = colorTabs,
                    selectedTabIndex = selectedColorTab,
                    onTabSelected = { index ->
                        settings.colorMode = when (index) {
                            0 -> ColorMode.SYSTEM
                            1 -> ColorMode.LIGHT
                            else -> ColorMode.DARK
                        }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                SwitchPreference(
                    title = "纯黑背景 AMOLED",
                    summary = "深色模式下使用纯黑背景，省电并适合 OLED 屏幕",
                    checked = settings.colorMode == ColorMode.AMOLED,
                    onCheckedChange = { enabled ->
                        settings.colorMode = if (enabled) ColorMode.AMOLED else ColorMode.DARK
                    },
                    enabled = settings.colorMode == ColorMode.DARK || settings.colorMode == ColorMode.AMOLED,
                )
            }

            item {
                SmallTitle("动态取色")
                SwitchPreference(
                    title = "Monet 动态取色",
                    summary = if (settings.monet) "跟随系统壁纸配色" else "使用下方预设主题色",
                    checked = settings.monet,
                    onCheckedChange = { settings.monet = it },
                )
                OverlayDropdownPreference(
                    title = "主题色",
                    summary = "当前：${paletteNameOf(settings.keyColor)}",
                    items = paletteItems,
                    selectedIndex = paletteIndex,
                    onSelectedIndexChange = { index -> settings.keyColor = ThemePalette[index].argb },
                    showValue = true,
                )
                if (settings.monet) {
                    OverlayDropdownPreference(
                        title = "调色板风格",
                        summary = "Monet 色彩生成风格",
                        items = styleItems,
                        selectedIndex = styleIndex,
                        onSelectedIndexChange = { index ->
                            settings.colorStyle = ThemePaletteStyle.entries[index]
                        },
                        showValue = true,
                    )
                }
                if (settings.keyColor != 0) {
                    OverlayDropdownPreference(
                        title = "规范版本",
                        summary = "主题色生成规范",
                        items = specItems,
                        selectedIndex = specIndex,
                        onSelectedIndexChange = { index ->
                            settings.colorSpec = if (index == 1) ThemeColorSpec.Spec2025
                            else ThemeColorSpec.Spec2021
                        },
                        showValue = true,
                    )
                }
            }

            item {
                SmallTitle("底栏")
                OverlayDropdownPreference(
                    title = "底栏形态",
                    summary = "切换后立即生效",
                    items = barItems,
                    selectedIndex = barIndex,
                    onSelectedIndexChange = { index ->
                        settings.bottomBarStyle = BottomBarStyle.entries[index]
                    },
                    showValue = true,
                )
            }

            item {
                SmallTitle("效果")
                SwitchPreference(
                    title = "启用模糊",
                    summary = if (blurSupported) "玻璃/毛玻璃效果总开关"
                    else "本机不支持 RuntimeShader，玻璃自动降级为不透明",
                    checked = settings.enableBlur && blurSupported,
                    onCheckedChange = { settings.enableBlur = it },
                    enabled = blurSupported,
                )
                SwitchPreference(
                    title = "玻璃底栏",
                    summary = if (settings.bottomBarStyle == BottomBarStyle.LIQUID_GLASS)
                        "液态玻璃形态已选中" else "选中液态玻璃底栏后生效",
                    checked = settings.bottomBarStyle == BottomBarStyle.LIQUID_GLASS && settings.enableBlur,
                    onCheckedChange = { enabled ->
                        settings.bottomBarStyle = if (enabled) BottomBarStyle.LIQUID_GLASS
                        else BottomBarStyle.FLOATING
                    },
                    enabled = blurSupported && settings.enableBlur,
                )
                SwitchPreference(
                    title = "预测性返回手势",
                    summary = "Android 14+ 返回时显示系统预览动画",
                    checked = settings.predictiveBack,
                    onCheckedChange = { settings.predictiveBack = it },
                    enabled = android.os.Build.VERSION.SDK_INT >= 34,
                )
                SliderPreference(
                    title = "页面缩放",
                    summary = "全局界面密度",
                    value = settings.pageScale,
                    onValueChange = { settings.pageScale = it },
                    valueText = "${(settings.pageScale * 100).toInt()}%",
                    valueRange = 0.8f..1.1f,
                    steps = 29,
                )
            }

            item { Spacer(Modifier.height(28.dp)) }
        }
    }
}

private fun ThemePaletteStyle.displayName(): String = when (this) {
    ThemePaletteStyle.TonalSpot -> "TonalSpot"
    ThemePaletteStyle.Neutral -> "Neutral"
    ThemePaletteStyle.Vibrant -> "Vibrant"
    ThemePaletteStyle.Expressive -> "Expressive"
    ThemePaletteStyle.Rainbow -> "Rainbow"
    ThemePaletteStyle.FruitSalad -> "FruitSalad"
    ThemePaletteStyle.Monochrome -> "Monochrome"
    ThemePaletteStyle.Fidelity -> "Fidelity"
    ThemePaletteStyle.Content -> "Content"
}
