package com.lxithral.mjegg.platform

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** 深色模式四态。 */
enum class ColorMode { SYSTEM, LIGHT, DARK, AMOLED }

/** 底栏三种形态。 */
enum class BottomBarStyle { STANDARD, FLOATING, LIQUID_GLASS }

/**
 * 设置存储: SharedPreferences("settings") + Compose 可观察状态。
 *
 * key 命名沿用设计指南(与 KernelSU/Mishka 对齐), 方便后续把壳换成别的业务。
 * 所有外观开关都是"改了就立即生效并持久化"。
 */
class SettingsStore private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---------- 外观 ----------
    var colorMode by mutableStateOf(
        runCatching { ColorMode.valueOf(prefs.getString(K_COLOR_MODE, null) ?: "SYSTEM") }
            .getOrDefault(ColorMode.SYSTEM)
    )
        private set

    var monet by mutableStateOf(prefs.getBoolean(K_MONET, false))
        private set

    var keyColor by mutableStateOf(prefs.getInt(K_KEY_COLOR, DEFAULT_KEY_COLOR))
        private set

    var enableBlur by mutableStateOf(prefs.getBoolean(K_ENABLE_BLUR, true))
        private set

    var bottomBarStyle by mutableStateOf(
        runCatching { BottomBarStyle.valueOf(prefs.getString(K_BOTTOM_BAR, null) ?: "STANDARD") }
            .getOrDefault(BottomBarStyle.STANDARD)
    )
        private set

    var floatingBottomBarBlur by mutableStateOf(prefs.getBoolean(K_FLOATING_BLUR, true))
        private set

    var navigationBadge by mutableStateOf(prefs.getBoolean(K_NAV_BADGE, true))
        private set

    var predictiveBack by mutableStateOf(prefs.getBoolean(K_PREDICTIVE_BACK, true))
        private set

    // ---------- 彩蛋 ----------
    var eggEnabled by mutableStateOf(prefs.getBoolean(K_EGG_ENABLED, true))
        private set

    var targetWeChat by mutableStateOf(prefs.getBoolean(K_TARGET_WECHAT, true))
        private set

    var targetQQ by mutableStateOf(prefs.getBoolean(K_TARGET_QQ, true))
        private set

    var targetDingTalk by mutableStateOf(prefs.getBoolean(K_TARGET_DINGTALK, true))
        private set

    var volume by mutableStateOf(prefs.getInt(K_VOLUME, 100))
        private set

    var overlayHeight by mutableStateOf(prefs.getInt(K_HEIGHT, 85))
        private set

    var cooldownSeconds by mutableStateOf(prefs.getInt(K_COOLDOWN, 4))
        private set

    /** 累计触发次数(展示用, 不持久化统计口径之外的东西) */
    var triggerCount by mutableIntStateOf(prefs.getInt(K_TRIGGER_COUNT, 0))
        private set

    // ---------- 写入 ----------
    fun updateColorMode(v: ColorMode) {
        colorMode = v
        prefs.edit().putString(K_COLOR_MODE, v.name).apply()
    }

    fun updateMonet(v: Boolean) {
        monet = v
        prefs.edit().putBoolean(K_MONET, v).apply()
    }

    fun updateKeyColor(v: Int) {
        keyColor = v
        prefs.edit().putInt(K_KEY_COLOR, v).apply()
    }

    fun updateEnableBlur(v: Boolean) {
        enableBlur = v
        prefs.edit().putBoolean(K_ENABLE_BLUR, v).apply()
    }

    fun updateBottomBarStyle(v: BottomBarStyle) {
        bottomBarStyle = v
        prefs.edit().putString(K_BOTTOM_BAR, v.name).apply()
    }

    fun updateFloatingBottomBarBlur(v: Boolean) {
        floatingBottomBarBlur = v
        prefs.edit().putBoolean(K_FLOATING_BLUR, v).apply()
    }

    fun updateNavigationBadge(v: Boolean) {
        navigationBadge = v
        prefs.edit().putBoolean(K_NAV_BADGE, v).apply()
    }

    fun updatePredictiveBack(v: Boolean) {
        predictiveBack = v
        prefs.edit().putBoolean(K_PREDICTIVE_BACK, v).apply()
    }

    fun updateEggEnabled(v: Boolean) {
        eggEnabled = v
        prefs.edit().putBoolean(K_EGG_ENABLED, v).apply()
    }

    fun updateTargetWeChat(v: Boolean) {
        targetWeChat = v
        prefs.edit().putBoolean(K_TARGET_WECHAT, v).apply()
    }

    fun updateTargetQQ(v: Boolean) {
        targetQQ = v
        prefs.edit().putBoolean(K_TARGET_QQ, v).apply()
    }

    fun updateTargetDingTalk(v: Boolean) {
        targetDingTalk = v
        prefs.edit().putBoolean(K_TARGET_DINGTALK, v).apply()
    }

    fun updateVolume(v: Int) {
        volume = v
        prefs.edit().putInt(K_VOLUME, v).apply()
    }

    fun updateOverlayHeight(v: Int) {
        overlayHeight = v
        prefs.edit().putInt(K_HEIGHT, v).apply()
    }

    fun updateCooldownSeconds(v: Int) {
        cooldownSeconds = v
        prefs.edit().putInt(K_COOLDOWN, v).apply()
    }

    fun bumpTriggerCount() {
        triggerCount += 1
        prefs.edit().putInt(K_TRIGGER_COUNT, triggerCount).apply()
    }

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
        private const val K_ENABLE_BLUR = "enable_blur"
        private const val K_BOTTOM_BAR = "bottom_bar_style"
        private const val K_FLOATING_BLUR = "enable_floating_bottom_bar_blur"
        private const val K_NAV_BADGE = "enable_navigation_badge"
        private const val K_PREDICTIVE_BACK = "enable_predictive_back"
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

        @Volatile
        private var instance: SettingsStore? = null

        fun get(context: Context): SettingsStore =
            instance ?: synchronized(this) {
                instance ?: SettingsStore(context).also { instance = it }
            }
    }
}
