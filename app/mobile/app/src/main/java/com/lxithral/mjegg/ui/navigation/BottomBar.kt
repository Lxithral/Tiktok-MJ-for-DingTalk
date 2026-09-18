package com.lxithral.mjegg.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lxithral.mjegg.platform.BottomBarStyle
import com.lxithral.mjegg.platform.SettingsStore
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Badge
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarDisplayMode
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 底栏三形态 —— 本壳的核心卖点, 由 `bottom_bar_style` 决定:
 *
 *  A. STANDARD      标准 miuix NavigationBar(贴底全宽)
 *  B. FLOATING      悬浮胶囊 FloatingNavigationBar(左右 12dp)
 *  C. LIQUID_GLASS  悬浮 + miuix-blur 玻璃质感(色散模糊 + 玻璃高光描边)
 */
@Composable
fun BottomBar(
    settings: SettingsStore,
    pagerState: PagerState,
    backdrop: LayerBackdrop,
    isDark: Boolean,
) {
    val scope = rememberCoroutineScope()

    val items = listOf(
        "主页" to MiuixIcons.Home,
        "功能" to MiuixIcons.GridView,
        "模块" to MiuixIcons.Layers,
        "设置" to MiuixIcons.Settings,
    )

    // 角标: 演示用 —— 模块页有"更新"显示红点, 受总开关控制
    val badgeFor: @Composable (Int) -> Unit = { index ->
        if (settings.navigationBadge && index == 2) {
            Badge(containerColor = MiuixTheme.colorScheme.error)
        }
    }

    val select: (Int) -> Unit = { index ->
        scope.launch { pagerState.animateScrollToPage(index) }
    }

    val blurAvailable = settings.enableBlur && isRuntimeShaderSupported()

    when (settings.bottomBarStyle) {
        BottomBarStyle.STANDARD -> {
            NavigationBar(
                mode = NavigationBarDisplayMode.IconAndText,
                showDivider = true,
            ) {
                items.forEachIndexed { index, (label, icon) ->
                    NavigationBarItem(
                        selected = pagerState.currentPage == index,
                        onClick = { select(index) },
                        icon = icon,
                        label = label,
                        badge = if (settings.navigationBadge && index == 2) {
                            { badgeFor(index) }
                        } else null,
                    )
                }
            }
        }

        BottomBarStyle.FLOATING -> {
            FloatingNavigationBar(horizontalOutSidePadding = 12.dp) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    items.forEachIndexed { index, (label, icon) ->
                        FloatingNavigationBarItem(
                            selected = pagerState.currentPage == index,
                            onClick = { select(index) },
                            icon = icon,
                            label = label,
                            badge = if (settings.navigationBadge && index == 2) {
                                { badgeFor(index) }
                            } else null,
                        )
                    }
                }
            }
        }

        BottomBarStyle.LIQUID_GLASS -> {
            FloatingNavigationBar(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .textureBlur(
                        backdrop = backdrop,
                        shape = RoundedCornerShape(50),
                        blurRadius = 25f,
                        colors = BlurColors(
                            blendColors = listOf(
                                BlendColorEntry(
                                    MiuixTheme.colorScheme.surfaceContainer
                                        .copy(alpha = 0.6f)
                                )
                            )
                        ),
                        highlight = if (isDark) Highlight.GlassStrokeSmallDark
                        else Highlight.GlassStrokeSmallLight,
                        enabled = blurAvailable,
                    ),
                color = Color.Transparent,
                showDivider = false,
                horizontalOutSidePadding = 12.dp,
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    items.forEachIndexed { index, (label, icon) ->
                        FloatingNavigationBarItem(
                            selected = pagerState.currentPage == index,
                            onClick = { select(index) },
                            icon = icon,
                            label = label,
                            badge = if (settings.navigationBadge && index == 2) {
                                { badgeFor(index) }
                            } else null,
                        )
                    }
                }
            }
        }
    }
}
