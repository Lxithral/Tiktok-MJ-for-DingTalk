package com.lxithral.mjegg.ui.component

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Process
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.theme.MiuixTheme

/*
 * 保后台(保活)组件 —— 解决"服务显示已连接但收不到事件"的运行时根因:
 *   HyperOS 省电/后台策略会冻结纯服务进程(息屏久了发 mj 没反应), 重装 APK 后
 *   事件派发也会被冻住(重启手机或重开无障碍恢复)。
 *
 * 行样式严格走 miuix 标准: BasicComponent(56dp 高 / 16dp 内边距 / 17sp Medium 标题 /
 * 14sp summary) + 尾部配件(已开启=Check 20dp primary, 未开启/未知=ArrowRight 10×16dp)。
 * 用法: OOBE「保活设置」页与设置页「保后台」组共用 [KeepAliveRows], 调用方包 Card。
 */

private val ICON_BLUE = Color(0xFF3482FF)    // 线描图标(provision 蓝)

/**
 * 状态行 —— miuix BasicComponent 定制行, 反馈语义照 HyperCeiler PermissionItemView:
 * checked=true 显示对勾(Check 20dp primary); 未开启默认**空白占位**(对勾位隐形),
 * showArrowWhenOff=true 时改显右箭头(用于"点按去开启"的引导场景)。
 * checked=null 表示"探测不到"(界面 summary 应写明无法检测)。
 */
@Composable
fun StatusRow(
    title: String,
    checked: Boolean?,
    summary: String? = null,
    showArrowWhenOff: Boolean = false,
    onClick: () -> Unit,
) {
    BasicComponent(
        title = title,
        summary = summary,
        onClick = onClick,
        endActions = { StatusRowEnd(checked, showArrowWhenOff) },
    )
}

/** 状态文字(反馈"开了/没开"), 供 summary 拼接。 */
fun statusTextOf(checked: Boolean?): String = when (checked) {
    true -> "已开启"
    false -> "未开启"
    null -> "无法检测"
}

@Composable
private fun RowScope.StatusRowEnd(checked: Boolean?, showArrowWhenOff: Boolean) {
    when {
        checked == true -> Icon(
            imageVector = MiuixIcons.Basic.Check,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        showArrowWhenOff -> Icon(
            imageVector = MiuixIcons.Basic.ArrowRight,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.onSurfaceContainerVariant,
            modifier = Modifier.size(10.dp, 16.dp),
        )
        else -> Spacer(Modifier.size(20.dp))   // HC 语义: 未勾选时对勾位隐形
    }
}

/** 保活页预览图标: 蓝色线描挂锁(70dp, 对齐 provision 线描风格)。 */
@Composable
fun LockIcon() {
    Canvas(Modifier.size(70.dp)) {
        val u = size.minDimension / 100f
        val stroke = Stroke(width = 7f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val body = Path().apply {
            addRoundRect(RoundRect(22f * u, 44f * u, 78f * u, 88f * u, CornerRadius(12f * u, 12f * u)))
        }
        drawPath(body, ICON_BLUE, style = stroke)
        val shackle = Path().apply {
            moveTo(34f * u, 46f * u)
            lineTo(34f * u, 33f * u)
            cubicTo(34f * u, 18f * u, 66f * u, 18f * u, 66f * u, 33f * u)
            lineTo(66f * u, 46f * u)
        }
        drawPath(shackle, ICON_BLUE, style = stroke)
    }
}

// ---------- 系统状态读取与跳转 ----------

/** 是否已加入电池优化白名单(≈ MIUI「省电策略=无限制」)。 */
fun isIgnoringBatteryOptimizations(context: Context): Boolean = runCatching {
    val pm = context.getSystemService(PowerManager::class.java)
    pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
}.getOrDefault(false)

/** 弹系统对话框申请忽略电池优化; 不支持时退到电池优化列表。 */
fun requestIgnoreBatteryOptimizations(context: Context) {
    if (isIgnoringBatteryOptimizations(context)) return
    runCatching {
        context.startActivity(
            Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.onFailure {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

/**
 * 自启动是否已允许。MIUI/HyperOS 用 appops `android:auto_start`(数值 op 10008)记录,
 * 两条探测路径都拿不到真实状态时返回 null(界面显示"无法检测")。
 */
fun isAutoStartAllowed(context: Context): Boolean? {
    val appOps = context.getSystemService(AppOpsManager::class.java) ?: return null
    val modes = listOfNotNull(
        runCatching {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow("android:auto_start", Process.myUid(), context.packageName)
        }.getOrNull(),
        // 数值 op 10008(MIUI OP_AUTO_START)在公开 SDK 无重载, 走反射尽力探测
        runCatching {
            val m = AppOpsManager::class.java.getMethod(
                "checkOpNoThrow",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                String::class.java,
            )
            m.invoke(appOps, 10008, Process.myUid(), context.packageName) as Int
        }.getOrNull(),
    )
    for (mode in modes) {
        when (mode) {
            AppOpsManager.MODE_ALLOWED -> return true
            AppOpsManager.MODE_IGNORED -> return false
            // MODE_ERRORED / DEFAULT = 这条路径没探到, 换下一条
        }
    }
    return null
}

/**
 * 锁定后台是否已开启。MIUI 的"最近任务锁定"没有公开查询接口 ——
 * 这里对 MIUI 扩展 appops 名做尽力探测, 名字不存在会抛异常 → null(界面不显对勾)。
 */
fun isBackgroundLocked(context: Context): Boolean? {
    val appOps = context.getSystemService(AppOpsManager::class.java) ?: return null
    for (op in listOf("miui:lock_background", "android:locked_app")) {
        val mode = runCatching {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(op, Process.myUid(), context.packageName)
        }.getOrNull() ?: continue
        when (mode) {
            AppOpsManager.MODE_ALLOWED -> return true
            else -> return null
        }
    }
    return null
}

/** 打开自启动管理(MIUI 安全中心), 不支持则退到应用详情页。 */
fun openAutoStartSettings(context: Context) {
    val candidates = listOf(
        Intent().setComponent(
            ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
        ),
        Intent("miui.intent.action.OP_AUTO_START"),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
    )
    for (intent in candidates) {
        val ok = runCatching {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
        if (ok) return
    }
}

// ---------- 三项保活设置行 ----------

/**
 * 保活设置三行(自启动 / 忽略电池优化 / 锁定后台)。
 * 状态在 ON_RESUME 时刷新(从系统页返回后对勾自动更新)。不构成流程门控。
 */
@Composable
fun KeepAliveRows() {
    val context = LocalContext.current
    var refreshTick by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var autoStart by remember { mutableStateOf(isAutoStartAllowed(context)) }
    var ignoringBattery by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }
    var bgLocked by remember { mutableStateOf(isBackgroundLocked(context)) }
    LaunchedEffect(refreshTick) {
        autoStart = isAutoStartAllowed(context)
        ignoringBattery = isIgnoringBatteryOptimizations(context)
        bgLocked = isBackgroundLocked(context)
    }

    Column {
        StatusRow(
            title = "允许自启动",
            summary = "${statusTextOf(autoStart)} · 被禁止时系统会冻结服务 收不到 mj",
            checked = autoStart,
            onClick = {
                openAutoStartSettings(context)
                autoStart = isAutoStartAllowed(context)
            },
        )
        StatusRow(
            title = "忽略电池优化",
            summary = "${statusTextOf(ignoringBattery)} · 省电策略设为无限制 息屏久了也能收事件",
            checked = ignoringBattery,
            onClick = {
                requestIgnoreBatteryOptimizations(context)
                ignoringBattery = isIgnoringBatteryOptimizations(context)
            },
        )
        StatusRow(
            title = "锁定后台",
            summary = "${statusTextOf(bgLocked)} · 在最近任务里把本应用卡片下拉加锁",
            checked = bgLocked,
            onClick = {
                Toast.makeText(
                    context,
                    "打开最近任务（多任务界面） 把「MJ 彩蛋」卡片往下拉即可锁定后台",
                    Toast.LENGTH_LONG,
                ).show()
                bgLocked = isBackgroundLocked(context)
            },
        )
    }
}
