package com.lxithral.mjegg.ui.navigation

import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.nav.core.NavKey

/**
 * 路由。返回栈靠 `Json.encodeToString<Route>` 多态编码整条栈持久化，
 * 所以每个路由都必须 `@Serializable`（进程被杀后能恢复回去的页面）。
 *
 * **基类这行 `@Serializable` 不能删**：看起来与各子类重复，删掉照样编译，
 * 但多态序列化器由基类注册，缺了它恢复返回栈时运行时抛 SerializationException。
 */
@Serializable
sealed interface Route : NavKey {

    /** OOBE 首启引导（欢迎 → 无障碍 → 完成），仅 `oobe_done=false` 时作为初始页。 */
    @Serializable
    data object Oobe : Route

    /** 主页：3 Tab 的 HorizontalPager 宿主。它是栈底，永远不弹。 */
    @Serializable
    data object Main : Route

    /** 主题设置二级页（本壳的灵魂）。 */
    @Serializable
    data object ThemeSettings : Route

    /** 诊断二级页。 */
    @Serializable
    data object Diagnostics : Route

    /** 关于二级页。 */
    @Serializable
    data object About : Route
}
