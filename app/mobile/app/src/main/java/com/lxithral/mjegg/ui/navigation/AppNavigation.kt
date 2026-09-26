package com.lxithral.mjegg.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.lxithral.mjegg.ui.MainScreen
import com.lxithral.mjegg.ui.screen.about.AboutScreen
import com.lxithral.mjegg.ui.screen.oobe.OobeScreen
import com.lxithral.mjegg.ui.screen.settings.DiagnosticsScreen
import com.lxithral.mjegg.ui.screen.settings.ThemeSettingsScreen
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.rememberNavBackStack
import top.yukonga.miuix.kmp.nav.core.rememberNavSystemCornerRadius
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection
import top.yukonga.miuix.kmp.nav.transition.NavTransitions

/**
 * 导航装配 —— 页面跳转与转场动画都在这里。
 *
 * 要点（对应指南第 3 节，缺一条就会出现历史缺陷）：
 * - 用 `miuix-nav` 的 [NavDisplay] + 返回栈，**不是** HorizontalPager 页索引或布尔值；
 * - `onBack = { navigator.pop() }` —— 系统返回键、全面屏手势、二级页返回按钮全走这里，
 *   所以"二级页返回直接回桌面"不会再出现；
 * - 转场用默认的 `NavTransitions.MiuixDefault`（滑动 + 0.25 倍视差 + 压暗），
 *   不额外写动画代码，也不许改成 None；
 * - `effects` 传转场圆角裁切，让新页以系统圆角滑入；
 * - 每个二级 entry 挂 `swipeDismiss`（横滑返回是 opt-in，`null` 表示关闭）；
 * - 根页 [Route.Main] 不给滑动关闭，否则会把主页划掉。
 */
@Composable
fun AppNavigation(settings: com.lxithral.mjegg.platform.SettingsStore) {
    // OOBE: 首次启动先进引导, 完成后 replace 成主页; 之后每次启动直接进主页
    val start = if (settings.oobeDone) Route.Main else Route.Oobe
    val backStack = rememberNavBackStack(start)
    val navigator = remember(backStack) { Navigator(backStack) }

    // 横滑返回方向是物理方向（miuix-nav 不自动镜像），RTL 下要反过来
    val swipeBackDirection = if (LocalLayoutDirection.current == LayoutDirection.Rtl) {
        NavSwipeDirection.RightToLeft
    } else {
        NavSwipeDirection.LeftToRight
    }

    CompositionLocalProvider(LocalNavigator provides navigator) {
        NavDisplay(
            backStack = backStack,
            onBack = { navigator.pop() },
            transition = NavTransitions.MiuixDefault,
            effects = NavDisplayEffects(
                cornerClipRadius = rememberNavSystemCornerRadius(),
            ),
        ) {
            entry<Route.Oobe> {
                OobeScreen(onDone = {
                    settings.oobeDone = true
                    if (navigator.backStackSize() > 1 && navigator.backStack.contains(Route.Main)) {
                        // 从开发者模式重新进入的: 弹回首屏(保留设置页等路径)
                        navigator.popUntil { it is Route.Main }
                    } else {
                        // 首启引导: 整栈替换为主页
                        navigator.replace(Route.Main)
                    }
                })
            }
            entry<Route.Main> {
                MainScreen(settings, onRerunOobe = {
                    settings.oobeDone = false
                    navigator.push(Route.Oobe)
                })
            }
            entry<Route.ThemeSettings>(swipeDismiss = swipeBackDirection) {
                ThemeSettingsScreen(settings = settings, onBack = { navigator.pop() })
            }
            entry<Route.Diagnostics>(swipeDismiss = swipeBackDirection) {
                DiagnosticsScreen(settings = settings, onBack = { navigator.pop() })
            }
            entry<Route.About>(swipeDismiss = swipeBackDirection) {
                AboutScreen(onBack = { navigator.pop() })
            }
        }
    }
}
