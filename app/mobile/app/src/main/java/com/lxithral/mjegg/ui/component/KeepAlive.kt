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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/*
 * 保后台(保活)组件 —— 解决"服务显示已连接但收不到事件"的运行时根因:
 *   HyperOS 省电/后台策略会冻结纯服务进程(息屏久了发 mj 没反应), 重装 APK 后
 *   事件派发也会被冻住(重启手机或重开无障碍才恢复)。
 *
 * 三项设置里「自启动」「忽略电池优化」可从 app 内引导完成,
 * 「锁定后台」系统无开放接口, 只能提示用户去最近任务下拉加锁。
 * 用法: OOBE「保活设置」页与设置页「保后台」组共用 [KeepAliveRows]。
 */

private val CHECK_BLUE = Color(0xFF277AF7)   // provision_picker_btn_radio
private val ICON_BLUE = Color(0xFF3482FF)    // 线描图标

/** 权限行 —— PermissionItemView 样式: 56dp 圆角行, 左标题, 右蓝 check(未勾=占位)。 */
@Composable
fun PermissionRow(
    title: String,
    checked: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MiuixTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
    ) {
        Text(
            text = title,
            color = MiuixTheme.colorScheme.onSurface,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.align(Alignment.CenterStart),
        )
        Box(modifier = Modifier.align(Alignment.CenterEnd)) {
            if (checked) CheckIcon()
            else Spacer(Modifier.size(24.dp))
        }
    }
}

/** provision_picker_btn_radio 64×64 蓝色对勾, 落位 24dp。 */
@Composable
fun CheckIcon() {
    Canvas(Modifier.size(24.dp)) {
        val s = size.width / 64f
        val path = Path().apply {
            moveTo(50.8171f * s, 22.1514f * s)
            cubicTo(52.0496f * s, 20.6624f * s, 51.8417f * s, 18.4561f * s, 50.3527f * s, 17.2235f * s)
            cubicTo(48.8636f * s, 15.991f * s, 46.6573f * s, 16.1989f * s, 45.4247f * s, 17.6879f * s)
            lineTo(26.9535f * s, 40.0031f * s)
            lineTo(17.4007f * s, 30.4502f * s)
            cubicTo(16.0338f * s, 29.0833f * s, 13.8177f * s, 29.0833f * s, 12.4509f * s, 30.4502f * s)
            cubicTo(11.0841f * s, 31.817f * s, 11.0841f * s, 34.0331f * s, 12.4509f * s, 35.3999f * s)
            lineTo(24.7077f * s, 47.6567f * s)
            cubicTo(25.7244f * s, 48.6734f * s, 27.2109f * s, 48.9338f * s, 28.4683f * s, 48.4381f * s)
            cubicTo(29.016f * s, 48.2302f * s, 29.519f * s, 47.8817f * s, 29.9192f * s, 47.3982f * s)
            close()
        }
        drawPath(path, CHECK_BLUE)
    }
}

/** 保活页预览图标: 蓝色线描挂锁(70dp)。 */
@Composable
fun LockIcon() {
    Canvas(Modifier.size(70.dp)) {
        val u = size.minDimension / 100f
        val stroke = Stroke(width = 6f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val body = Path().apply {
            addRoundRect(RoundRect(22f * u, 44f * u, 78f * u, 88f * u, CornerRadius(10f * u, 10f * u)))
        }
        drawPath(body, ICON_BLUE, style = stroke)
        val shackle = Path().apply {
            moveTo(34f * u, 46f * u)
            lineTo(34f * u, 32f * u)
            cubicTo(34f * u, 18f * u, 66f * u, 18f * u, 66f * u, 32f * u)
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
 * 自启动是否已允许。MIUI/HyperOS 用 appops `android:auto_start` 记录,
 * 不是公开常量 —— 读不到时返回 null(界面按"未确认"显示, 不显示对勾)。
 */
fun isAutoStartAllowed(context: Context): Boolean? = runCatching {
    val appOps = context.getSystemService(AppOpsManager::class.java) ?: return@runCatching null
    @Suppress("DEPRECATION")
    val mode = appOps.checkOpNoThrow("android:auto_start", Process.myUid(), context.packageName)
    when (mode) {
        AppOpsManager.MODE_ALLOWED -> true
        AppOpsManager.MODE_IGNORED, AppOpsManager.MODE_ERRORED -> false
        else -> null
    }
}.getOrNull()

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
    LaunchedEffect(refreshTick) {
        autoStart = isAutoStartAllowed(context)
        ignoringBattery = isIgnoringBatteryOptimizations(context)
    }

    Column(Modifier.fillMaxWidth()) {
        PermissionRow(
            title = "允许自启动",
            checked = autoStart == true,
            onClick = {
                openAutoStartSettings(context)
                autoStart = isAutoStartAllowed(context)
            },
        )
        Spacer(Modifier.height(10.dp))
        PermissionRow(
            title = "忽略电池优化（省电无限制）",
            checked = ignoringBattery,
            onClick = {
                requestIgnoreBatteryOptimizations(context)
                ignoringBattery = isIgnoringBatteryOptimizations(context)
            },
        )
        Spacer(Modifier.height(10.dp))
        PermissionRow(
            title = "锁定后台",
            checked = false,
            onClick = {
                Toast.makeText(
                    context,
                    "打开最近任务（多任务界面），把「MJ 彩蛋」卡片往下拉即可锁定后台",
                    Toast.LENGTH_LONG,
                ).show()
            },
        )
    }
}
