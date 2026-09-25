package com.lxithral.mjegg.ui.navigation

import androidx.compose.runtime.staticCompositionLocalOf
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.nav.core.NavKey

/**
 * 返回栈封装。页面跳转一律走这里 ——
 * **不要**再用 `rememberSaveable { mutableStateOf(false) }` 之类模拟页面跳转，
 * 那样系统返回/全面屏手势不会被接管，表现为"二级页返回直接回桌面"。
 */
class Navigator(
    val backStack: NavBackStack,
) {
    fun push(key: NavKey) {
        if (key !in backStack) backStack.add(key)
    }

    fun replace(key: NavKey) {
        if (backStack.isNotEmpty()) {
            backStack[backStack.lastIndex] = key
        } else {
            backStack.add(key)
        }
    }

    /** 栈底不动，只剩一层时交给系统退出 App。 */
    fun pop() {
        if (backStack.size > 1) backStack.removeLastOrNull()
    }

    fun popUntil(predicate: (NavKey) -> Boolean) {
        while (backStack.size > 1 && !predicate(backStack.last())) {
            backStack.removeAt(backStack.lastIndex)
        }
    }

    fun current(): NavKey? = backStack.lastOrNull()

    fun backStackSize(): Int = backStack.size
}

val LocalNavigator = staticCompositionLocalOf<Navigator> {
    error("LocalNavigator 未提供")
}
