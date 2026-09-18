package com.lxithral.mjegg.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lxithral.mjegg.platform.BottomBarStyle
import com.lxithral.mjegg.platform.ColorMode
import com.lxithral.mjegg.platform.SettingsStore
import com.lxithral.mjegg.ui.theme.ThemePalette
import com.lxithral.mjegg.ui.theme.paletteNameOf
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 主题设置页 —— 本壳的灵魂: 所有外观开关改完立即生效并持久化。
 *
 * 覆盖: 深色四态 / Monet 动态取色 / 15 色预设 / 模糊 / 底栏三形态 / 导航角标 / 预测性返回。
 */
@Composable
fun ThemeSettingsScreen(settings: SettingsStore, onBack: () -> Unit) {
    val blurSupported = isRuntimeShaderSupported()

    Scaffold(
        topBar = {
            TopAppBar(
                title = "主题设置",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Back, contentDescription = "返回")
                    }
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            SmallTitle("深色模式")
            Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                OptionRow(
                    options = ColorMode.entries.map { it to it.label() },
                    selected = settings.colorMode,
                    onSelect = settings::updateColorMode,
                )
            }

            SmallTitle("动态取色")
            Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                BasicComponent(
                    title = "Monet 动态取色",
                    summary = if (settings.monet) "从壁纸或主题色推导整套配色" else "使用固定预设配色",
                    endActions = {
                        Switch(checked = settings.monet, onCheckedChange = settings::updateMonet)
                    },
                )
                BasicComponent(
                    title = "当前主题色",
                    summary = paletteNameOf(settings.keyColor),
                )
            }

            SmallTitle("主题色")
            Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    ThemePalette.chunked(8).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            rowItems.forEach { entry ->
                                ColorChip(
                                    argb = entry.argb,
                                    selected = settings.keyColor == entry.argb,
                                    onClick = { settings.updateKeyColor(entry.argb) },
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    Text(
                        text = "选「壁纸」时由系统壁纸取色（需开启 Monet）。",
                        color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                        fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                    )
                }
            }

            SmallTitle("底栏形态")
            Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                OptionRow(
                    options = BottomBarStyle.entries.map { it to it.label() },
                    selected = settings.bottomBarStyle,
                    onSelect = settings::updateBottomBarStyle,
                )
            }

            SmallTitle("效果")
            Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                BasicComponent(
                    title = "启用模糊",
                    summary = if (blurSupported) "玻璃/毛玻璃效果总开关"
                    else "本机 API 低于 33，玻璃会自动降级为不透明",
                    endActions = {
                        Switch(
                            checked = settings.enableBlur && blurSupported,
                            onCheckedChange = settings::updateEnableBlur,
                            enabled = blurSupported,
                        )
                    },
                )
                BasicComponent(
                    title = "玻璃底栏",
                    summary = if (settings.bottomBarStyle == BottomBarStyle.LIQUID_GLASS)
                        "当前形态已启用" else "选择「液态玻璃底栏」后生效",
                    endActions = {
                        Switch(
                            checked = settings.floatingBottomBarBlur,
                            onCheckedChange = settings::updateFloatingBottomBarBlur,
                            enabled = blurSupported && settings.enableBlur,
                        )
                    },
                )
                BasicComponent(
                    title = "显示导航角标",
                    summary = "模块页有更新时在底栏显示红点",
                    endActions = {
                        Switch(
                            checked = settings.navigationBadge,
                            onCheckedChange = settings::updateNavigationBadge,
                        )
                    },
                )
                BasicComponent(
                    title = "预测性返回手势",
                    summary = "二级页返回时跟随手势进度做转场",
                    endActions = {
                        Switch(
                            checked = settings.predictiveBack,
                            onCheckedChange = settings::updatePredictiveBack,
                        )
                    },
                )
            }

            Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
                Text(
                    text = "所有开关都写在 SharedPreferences(\"settings\") 里，" +
                            "杀掉进程重开依然保留。",
                    modifier = Modifier.padding(16.dp),
                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

/** 一排互斥选项(用小卡片代替 Spinner, 少一层弹窗)。 */
@Composable
private fun <T> OptionRow(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isSelected) MiuixTheme.colorScheme.primaryContainer
                        else MiuixTheme.colorScheme.surfaceContainerHigh
                    )
                    .clickable { onSelect(value) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = if (isSelected) MiuixTheme.colorScheme.onPrimaryContainer
                    else MiuixTheme.colorScheme.onSurfaceContainer,
                    fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

/** 圆形色块; argb == 0 表示"跟随壁纸", 用一个渐变环表示。 */
@Composable
private fun ColorChip(
    argb: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val isWallpaper = argb == 0
    val color = if (isWallpaper) MiuixTheme.colorScheme.surfaceContainerHighest else Color(argb)
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MiuixTheme.colorScheme.primary
                else MiuixTheme.colorScheme.outline,
                shape = CircleShape,
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        if (isWallpaper) {
            Text(
                text = "壁",
                color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                fontSize = MiuixTheme.textStyles.footnote2.fontSize,
            )
        }
    }
}
