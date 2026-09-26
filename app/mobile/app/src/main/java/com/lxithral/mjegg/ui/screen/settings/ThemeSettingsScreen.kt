package com.lxithral.mjegg.ui.screen.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
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
import top.yukonga.miuix.kmp.basic.Card
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
import top.yukonga.miuix.kmp.icon.extended.Forward
import top.yukonga.miuix.kmp.icon.extended.Hide
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.Photos
import top.yukonga.miuix.kmp.icon.extended.Theme
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.icon.extended.Update
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

/**
 * 主题设置二级页（按套壳指南 02 = KernelSU ColorPaletteScreenMiuix 拆解）:
 *
 * - 大标题折叠: MiuixScrollBehavior + LazyColumn nestedScroll;
 * - 深色模式: TabRow 三段 + AMOLED 纯黑叠加;
 * - 配色卡层层展开: Monet 开关 → 主题色(16 色) → 调色板风格/规范版本(选了自定义色才出现);
 * - 底栏: 形态三选一; 效果: 启用模糊(API 33+ 才显示)/预测性返回(API 34+ 才显示);
 * - 每行左侧小图标(startAction)。
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
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
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
                Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                    SwitchPreference(
                        title = "纯黑背景 AMOLED",
                        summary = "深色模式下使用纯黑背景，省电并适合 OLED 屏幕",
                        checked = settings.colorMode == ColorMode.AMOLED,
                        onCheckedChange = { enabled ->
                            settings.colorMode = if (enabled) ColorMode.AMOLED else ColorMode.DARK
                        },
                        enabled = settings.colorMode == ColorMode.DARK || settings.colorMode == ColorMode.AMOLED,
                        startAction = { RowIcon(MiuixIcons.Theme) },
                    )
                }
            }

            item {
                SmallTitle("动态取色")
                Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                    SwitchPreference(
                        title = "Monet 动态取色",
                        summary = if (settings.monet) "跟随系统壁纸配色" else "使用下方预设主题色",
                        checked = settings.monet,
                        onCheckedChange = { settings.monet = it },
                        startAction = { RowIcon(MiuixIcons.Theme) },
                    )
                    AnimatedVisibility(visible = settings.monet) {
                        Column {
                            OverlayDropdownPreference(
                                title = "主题色",
                                summary = "当前：${paletteNameOf(settings.keyColor)}",
                                items = paletteItems,
                                selectedIndex = paletteIndex,
                                onSelectedIndexChange = { index -> settings.keyColor = ThemePalette[index].argb },
                                showValue = true,
                                startAction = { RowIcon(MiuixIcons.Tune) },
                            )
                            AnimatedVisibility(visible = settings.keyColor != 0) {
                                Column {
                                    OverlayDropdownPreference(
                                        title = "调色板风格",
                                        summary = "Monet 色彩生成风格",
                                        items = styleItems,
                                        selectedIndex = styleIndex,
                                        onSelectedIndexChange = { index ->
                                            settings.colorStyle = ThemePaletteStyle.entries[index]
                                        },
                                        showValue = true,
                                        startAction = { RowIcon(MiuixIcons.Photos) },
                                    )
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
                                        startAction = { RowIcon(MiuixIcons.Update) },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                SmallTitle("底栏")
                Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                    OverlayDropdownPreference(
                        title = "底栏形态",
                        summary = "切换后立即生效",
                        items = barItems,
                        selectedIndex = barIndex,
                        onSelectedIndexChange = { index ->
                            settings.bottomBarStyle = BottomBarStyle.entries[index]
                        },
                        showValue = true,
                        startAction = { RowIcon(MiuixIcons.Layers) },
                    )
                }
            }

            item {
                SmallTitle("效果")
                Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                    if (blurSupported) {
                        SwitchPreference(
                            title = "启用模糊",
                            summary = "玻璃/毛玻璃效果总开关",
                            checked = settings.enableBlur,
                            onCheckedChange = { settings.enableBlur = it },
                            startAction = { RowIcon(MiuixIcons.Hide) },
                        )
                    }
                    SwitchPreference(
                        title = "预测性返回手势",
                        summary = if (android.os.Build.VERSION.SDK_INT >= 34)
                            "返回时显示系统预览动画" else "需要 Android 14+",
                        checked = settings.predictiveBack,
                        onCheckedChange = { settings.predictiveBack = it },
                        enabled = android.os.Build.VERSION.SDK_INT >= 34,
                        startAction = { RowIcon(MiuixIcons.Forward) },
                    )
                }
            }

            item {
                SmallTitle("页面缩放")
                Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                    SliderPreference(
                        title = "全局界面密度",
                        value = settings.pageScale,
                        onValueChange = { settings.pageScale = it },
                        valueText = "${(settings.pageScale * 100).toInt()}%",
                        valueRange = 0.8f..1.1f,
                    )
                }
            }
        }
    }
}

@Composable
private fun RowIcon(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.padding(end = 6.dp),
        tint = MiuixTheme.colorScheme.onBackground,
    )
}

private fun ThemePaletteStyle.displayName(): String = when (this) {
    ThemePaletteStyle.TonalSpot -> "色调点"
    ThemePaletteStyle.Neutral -> "中性"
    ThemePaletteStyle.Vibrant -> "鲜艳"
    ThemePaletteStyle.Expressive -> "表现"
    ThemePaletteStyle.Rainbow -> "彩虹"
    ThemePaletteStyle.FruitSalad -> "水果沙拉"
    ThemePaletteStyle.Content -> "内容"
    else -> name
}
