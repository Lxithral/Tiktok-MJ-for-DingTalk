package com.lxithral.mjegg.platform

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

/** 深色模式四态。 */
enum class ColorMode { SYSTEM, LIGHT, DARK, AMOLED }

/** 底栏三种形态。 */
enum class BottomBarStyle { STANDARD, FLOATING, LIQUID_GLASS }

/**
 * 设置存储：SharedPreferences("settings") + Compose 可观察状态。
 *
 * key 命名沿用设计指南（与 KernelSU / Mishka 对齐），方便后续把壳换成别的业务。
 * 所有外观开关都是"改了就立即生效并持久化"——属性写成带自定义 setter 的 `var`，
 * 调用处直接 `settings.colorMode = ...` 即可，赋值即落盘。
 */
class SettingsStore private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---------- 外观 ----------
    private var colorModeState by mutableStateOf(
        enumOf(prefs.getString(K_COLOR_MODE, null), ColorMode.SYSTEM)
    )

    /** 深色模式：跟随系统 / 浅色 / 深色 / AMOLED 纯黑。 */
    var colorMode: ColorMode
        get() = colorModeState
        set(v) {
            colorModeState = v
            prefs.edit().putString(K_COLOR_MODE, v.name).apply()
        }

    private var monetState by mutableStateOf(prefs.getBoolean(K_MONET, false))

    /** Monet 动态取色：开 = 壁纸取色，关 = 预设主题色。 */
    var monet: Boolean
        get() = monetState
        set(v) {
            monetState = v
            prefs.edit().putBoolean(K_MONET, v).apply()
        }

    private var keyColorState by mutableStateOf(prefs.getInt(K_KEY_COLOR, DEFAULT_KEY_COLOR))

    /** 主题色预设（ARGB）。 */
    var keyColor: Int
        get() = keyColorState
        set(v) {
            keyColorState = v
            prefs.edit().putInt(K_KEY_COLOR, v).apply()
        }

    private var colorStyleState by mutableStateOf(
        enumOf(prefs.getString(K_COLOR_STYLE, null), ThemePaletteStyle.TonalSpot)
    )

    /** 调色板风格（Monet 开启时生效）。 */
    var colorStyle: ThemePaletteStyle
        get() = colorStyleState
        set(v) {
            colorStyleState = v
            prefs.edit().putString(K_COLOR_STYLE, v.name).apply()
        }

    private var colorSpecState by mutableStateOf(
        enumOf(prefs.getString(K_COLOR_SPEC, null), ThemeColorSpec.Spec2021)
    )

    /** 规范版本（2021 / 2025）。 */
    var colorSpec: ThemeColorSpec
        get() = colorSpecState
        set(v) {
            colorSpecState = v
            prefs.edit().putString(K_COLOR_SPEC, v.name).apply()
        }

    private var enableBlurState by mutableStateOf(prefs.getBoolean(K_ENABLE_BLUR, true))

    /** 玻璃/毛玻璃总开关。API < 33 时玻璃会自动降级为不透明。 */
    var enableBlur: Boolean
        get() = enableBlurState
        set(v) {
            enableBlurState = v
            prefs.edit().putBoolean(K_ENABLE_BLUR, v).apply()
        }

    private var bottomBarStyleState by mutableStateOf(
        enumOf(prefs.getString(K_BOTTOM_BAR, null), BottomBarStyle.STANDARD)
    )

    /** 底栏形态：标准 / 悬浮 / 液态玻璃。改完立即重组换形态。 */
    var bottomBarStyle: BottomBarStyle
        get() = bottomBarStyleState
        set(v) {
            bottomBarStyleState = v
            prefs.edit().putString(K_BOTTOM_BAR, v.name).apply()
        }

    private var navigationBadgeState by mutableStateOf(prefs.getBoolean(K_NAV_BADGE, true))

    /** 底栏角标总开关。 */
    var navigationBadge: Boolean
        get() = navigationBadgeState
        set(v) {
            navigationBadgeState = v
            prefs.edit().putBoolean(K_NAV_BADGE, v).apply()
        }

    private var predictiveBackState by mutableStateOf(prefs.getBoolean(K_PREDICTIVE_BACK, false))

    /** 预测性返回手势（API 34+，走运行时反射）。 */
    var predictiveBack: Boolean
        get() = predictiveBackState
        set(v) {
            predictiveBackState = v
            prefs.edit().putBoolean(K_PREDICTIVE_BACK, v).apply()
        }

    private var pageScaleState by mutableStateOf(prefs.getFloat(K_PAGE_SCALE, 1f))

    /** 页面缩放（0.8–1.1）。 */
    var pageScale: Float
        get() = pageScaleState
        set(v) {
            pageScaleState = v
            prefs.edit().putFloat(K_PAGE_SCALE, v).apply()
        }

    // ---------- 彩蛋 ----------
    private var eggEnabledState by mutableStateOf(prefs.getBoolean(K_EGG_ENABLED, true))

    var eggEnabled: Boolean
        get() = eggEnabledState
        set(v) {
            eggEnabledState = v
            prefs.edit().putBoolean(K_EGG_ENABLED, v).apply()
        }

    private var targetWeChatState by mutableStateOf(prefs.getBoolean(K_TARGET_WECHAT, true))
    var targetWeChat: Boolean
        get() = targetWeChatState
        set(v) {
            targetWeChatState = v
            prefs.edit().putBoolean(K_TARGET_WECHAT, v).apply()
        }

    private var targetQQState by mutableStateOf(prefs.getBoolean(K_TARGET_QQ, true))
    var targetQQ: Boolean
        get() = targetQQState
        set(v) {
            targetQQState = v
            prefs.edit().putBoolean(K_TARGET_QQ, v).apply()
        }

    private var targetDingTalkState by mutableStateOf(prefs.getBoolean(K_TARGET_DINGTALK, true))
    var targetDingTalk: Boolean
        get() = targetDingTalkState
        set(v) {
            targetDingTalkState = v
            prefs.edit().putBoolean(K_TARGET_DINGTALK, v).apply()
        }

    private var volumeState by mutableStateOf(prefs.getInt(K_VOLUME, 100))
    var volume: Int
        get() = volumeState
        set(v) {
            volumeState = v
            prefs.edit().putInt(K_VOLUME, v).apply()
        }

    private var overlayHeightState by mutableStateOf(prefs.getInt(K_HEIGHT, 85))
    var overlayHeight: Int
        get() = overlayHeightState
        set(v) {
            overlayHeightState = v
            prefs.edit().putInt(K_HEIGHT, v).apply()
        }

    private var cooldownSecondsState by mutableStateOf(prefs.getInt(K_COOLDOWN, 4))
    var cooldownSeconds: Int
        get() = cooldownSecondsState
        set(v) {
            cooldownSecondsState = v
            prefs.edit().putInt(K_COOLDOWN, v).apply()
        }

    private var triggerCountState by mutableIntStateOf(prefs.getInt(K_TRIGGER_COUNT, 0))

    /** 累计触发次数（展示用）。 */
    val triggerCount: Int
        get() = triggerCountState

    fun bumpTriggerCount() {
        triggerCountState += 1
        prefs.edit().putInt(K_TRIGGER_COUNT, triggerCountState).apply()
    }

    // 兼容已有业务页的命名式调用（属性 setter 仍是唯一写入实现）。
    fun updateColorMode(v: ColorMode) { colorMode = v }
    fun updateMonet(v: Boolean) { monet = v }
    fun updateKeyColor(v: Int) { keyColor = v }
    fun updateColorStyle(v: ThemePaletteStyle) { colorStyle = v }
    fun updateColorSpec(v: ThemeColorSpec) { colorSpec = v }
    fun updateEnableBlur(v: Boolean) { enableBlur = v }
    fun updateBottomBarStyle(v: BottomBarStyle) { bottomBarStyle = v }
    fun updateNavigationBadge(v: Boolean) { navigationBadge = v }
    fun updatePredictiveBack(v: Boolean) { predictiveBack = v }
    fun updatePageScale(v: Float) { pageScale = v.coerceIn(0.8f, 1.1f) }
    fun updateEggEnabled(v: Boolean) { eggEnabled = v }
    fun updateTargetWeChat(v: Boolean) { targetWeChat = v }
    fun updateTargetQQ(v: Boolean) { targetQQ = v }
    fun updateTargetDingTalk(v: Boolean) { targetDingTalk = v }
    fun updateVolume(v: Int) { volume = v.coerceIn(0, 100) }
    fun updateOverlayHeight(v: Int) { overlayHeight = v.coerceIn(30, 150) }
    fun updateCooldownSeconds(v: Int) { cooldownSeconds = v.coerceIn(0, 60) }

    /** 当前生效的监控包名集合。 */
    fun enabledPackages(): Set<String> = buildSet {
        if (targetWeChat) add(PKG_WECHAT)
        if (targetQQ) add(PKG_QQ)
        if (targetDingTalk) add(PKG_DINGTALK)
    }

    companion object {
        const val PREFS = "settings"

        private const val K_COLOR_MODE = "color_mode"
        private const val K_MONET = "miuix_monet"
        private const val K_KEY_COLOR = "key_color"
        private const val K_COLOR_STYLE = "color_style"
        private const val K_COLOR_SPEC = "color_spec"
        private const val K_ENABLE_BLUR = "enable_blur"
        private const val K_BOTTOM_BAR = "bottom_bar_style"
        private const val K_NAV_BADGE = "enable_navigation_badge"
        private const val K_PREDICTIVE_BACK = "enable_predictive_back"
        private const val K_PAGE_SCALE = "page_scale"
        private const val K_EGG_ENABLED = "egg_enabled"
        private const val K_TARGET_WECHAT = "target_wechat"
        private const val K_TARGET_QQ = "target_qq"
        private const val K_TARGET_DINGTALK = "target_dingtalk"
        private const val K_VOLUME = "volume"
        private const val K_HEIGHT = "overlay_height_ratio"
        private const val K_COOLDOWN = "cooldown_s"
        private const val K_TRIGGER_COUNT = "trigger_count"

        const val DEFAULT_KEY_COLOR = 0xFF3482FF.toInt()

        const val PKG_WECHAT = "com.tencent.mm"
        const val PKG_QQ = "com.tencent.mobileqq"
        const val PKG_DINGTALK = "com.alibaba.android.rimet"

        /** 枚举反序列化：存的是 name，取值失败一律回落到默认值（不抛异常）。 */
        private inline fun <reified T : Enum<T>> enumOf(name: String?, fallback: T): T =
            if (name == null) fallback
            else runCatching { enumValueOf<T>(name) }.getOrDefault(fallback)

        @Volatile
        private var instance: SettingsStore? = null

        fun get(context: Context): SettingsStore =
            instance ?: synchronized(this) {
                instance ?: SettingsStore(context).also { instance = it }
            }
    }
}
