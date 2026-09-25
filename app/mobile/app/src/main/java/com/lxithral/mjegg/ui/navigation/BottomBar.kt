package com.lxithral.mjegg.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lxithral.mjegg.platform.BottomBarStyle
import com.lxithral.mjegg.platform.SettingsStore
import com.lxithral.mjegg.ui.component.FloatingBottomBar
import com.lxithral.mjegg.ui.component.FloatingBottomBarItem
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Badge
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarDisplayMode
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.Settings

/**
 * 底栏三形态 —— 按指南 §6 接入：
 *
 * STANDARD      = miuix NavigationBar，贴底全宽；
 * FLOATING      = KernelSU 真实 FloatingBottomBar，胶囊贴内容宽、整体居中；
 * LIQUID_GLASS = 同一个 FloatingBottomBar 的 drawBackdrop + lens 分支，
 *                 API 不支持或总开关关闭时自动回退实体 surface。
 */
@Composable
fun BottomBar(
    settings: SettingsStore,
    pagerState: PagerState,
    backdrop: Backdrop,
    isDark: Boolean,
) {
    val scope = rememberCoroutineScope()
    val items = listOf(
        "主页" to MiuixIcons.Home,
        "功能" to MiuixIcons.GridView,
        "模块" to MiuixIcons.Layers,
        "设置" to MiuixIcons.Settings,
    )
    val select: (Int) -> Unit = { index ->
        scope.launch { pagerState.animateScrollToPage(index) }
    }
    val liquidEnabled = settings.bottomBarStyle == BottomBarStyle.LIQUID_GLASS &&
            settings.enableBlur && isRuntimeShaderSupported()

    @Composable
    fun BadgeContent(index: Int) {
        if (settings.navigationBadge && index == 2) {
            Badge()
        }
    }

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
                            { BadgeContent(index) }
                        } else null,
                    )
                }
            }
        }

        BottomBarStyle.FLOATING, BottomBarStyle.LIQUID_GLASS -> {
            // 指南硬指标：导航栏 inset≠0 时离底 8dp+inset，否则离底 28dp。
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.BottomCenter,
            ) {
                FloatingBottomBar(
                    modifier = Modifier.padding(start = 28.dp, end = 28.dp, bottom = 28.dp),
                    selectedIndex = pagerState.currentPage,
                    onSelected = select,
                    backdrop = backdrop,
                    tabsCount = items.size,
                    isBlurEnabled = liquidEnabled,
                ) {
                    items.forEachIndexed { index, (label, icon) ->
                        FloatingBottomBarItem(
                            selected = pagerState.currentPage == index,
                            onClick = { select(index) },
                            modifier = Modifier.defaultMinSize(minWidth = 76.dp),
                        ) {
                            Box(contentAlignment = Alignment.TopEnd) {
                                Icon(icon, contentDescription = label)
                                BadgeContent(index)
                            }
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}
