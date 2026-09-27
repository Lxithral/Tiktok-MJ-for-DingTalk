package com.lxithral.mjegg.ui.screen.oobe

import android.graphics.RuntimeShader
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lxithral.mjegg.R
import com.lxithral.mjegg.egg.MjAccessibilityService
import com.lxithral.mjegg.platform.SettingsStore
import com.lxithral.mjegg.ui.component.KeepAliveRows
import com.lxithral.mjegg.ui.component.LockIcon
import com.lxithral.mjegg.ui.component.StatusRow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

/*
 * OOBE 首启引导 —— 逐项对照 HyperCeiler `library/provision`（AGPL-3.0）+ 真机参考截图/录屏：
 * https://github.com/ReChronoRain/HyperCeiler
 *
 * 参照源文件：StartupFragment / AnimHelper / GlowController / GlowPainter / RenderViewLayout /
 * PermissionSettingsFragment / PermissionItemView / CongratulationFragment / ProvisionBaseActivity
 * + provision_startup_layout / provision_congratulation_layout / provision_permission_*.xml
 * + provision_slide_*.xml / enter_home_anim / provision_out_anim / colors.xml。
 *
 * 真机录屏校准（MiShare/temp 对比素材）：
 *   点圆钮转场 = makeScaleUpAnim：新页以圆角卡片从按钮 bounds 放大铺满，**内容随窗口缩放**、
 *   飞行中略半透明，旧页在后面**模糊**（不是黑块垫底）；返回 = 同动画反向缩回按钮。
 *   取色（1440×3200 参考图逐像素）：字标 #3939AB 深蓝紫、圆钮 #33356E 深蓝紫、
 *   完成页状态字 #515497。字标/按钮位置尺寸与参考完全一致，不动。
 *
 * MJ 适配：logo=红渐变+白蜘蛛（自绘）、字标「MJ 彩蛋」、线描预览图标、无障碍权限页。
 */

// —— Folme/系统缓动的贝塞尔等价 ——
private val CUBIC_OUT = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f)     // cubicOut
private val SIN_OUT = CubicBezierEasing(0.39f, 0.575f, 0.565f, 1f)       // sinOut
private val QUART_OUT = CubicBezierEasing(0.25f, 1f, 0.5f, 1f)           // quartOut
private val SINE_IN_OUT = CubicBezierEasing(0.37f, 0f, 0.63f, 1f)        // sine_in_out(pathInterpolator)

private const val DISPLAY_OS_ANDO_MS = 2500L    // displayOsAndoDelay 兜底
private const val BUTTON_IN_DELAY_MS = 1340L    // startPageBtnAnim setDelay(1340)
private const val BUTTON_IN_DUR_MS = 450        // FolmeEase.cubicOut(450)
private const val BUTTON_ENABLE_DELAY_MS = 530L  // 换页动画(500ms)播完 + 30ms 即可点
private const val MORPH_MS = 505                // makeScaleUpAnim 转场(照抄 505ms 恢复窗口)
private const val PAGE_SLIDE_MS = 500           // 页间翻页(放慢 + 视差淡出)

// —— 真机参考图逐像素取色（勿改）——
private val WORDMARK_INDIGO = Color(0xFF3939AB)   // 首屏/完成页字标
private val BUTTON_INDIGO = Color(0xFF33356E)    // 首屏圆钮（玻璃暗紫）
private val STATE_TEXT_INDIGO = Color(0xFF515497) // 完成页「设置完毕」
private val BTN_LITE = Color(0x99000000)         // provision_next_lite: 60% 黑圆
private const val FOREGROUND_FILL = 0x99000000   // anim_foreground_color(非模糊前景遮罩)
private val PROVISION_BLUE = Color(0xFF3482FF)   // provision_confirm_background / 线描图标
private val CHECK_BLUE = Color(0xFF277AF7)       // provision_picker_btn_radio
private val MJ_LOGO_RED = Color(0xFFE0342F)      // MJ logo 底色（原版红方块）

// 页间翻页缓动: 平滑进出(material standard), 比 accelerate_decelerate 更柔
private val SMOOTH = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

/** OOBE 流程页。Notes 仅在版本更新触发/开发者预览时插入首屏之后。 */
private enum class OobePage { Splash, Welcome, Permission, KeepAlive, Basic, Done }

@Composable
fun OobeScreen(settings: SettingsStore, onDone: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val shaderSupported = isRuntimeShaderSupported()
    val glowActive = shaderSupported
    val blurGlass = settings.enableBlur && shaderSupported

    // 流程页序: 首屏 → 欢迎 → 权限设置 → 保活设置 → 基础设置 → 完成
    // (用户决定: OOBE 不放更新日志, 只放欢迎页; 版本更新仍会重走一遍引导)
    val pages = remember {
        listOf(
            OobePage.Splash,
            OobePage.Welcome,
            OobePage.Permission,
            OobePage.KeepAlive,
            OobePage.Basic,
            OobePage.Done,
        )
    }
    var step by remember { mutableIntStateOf(0) }
    // needAdmission 语义: 只有真正首入首屏放圆环; 点按钮前进或返回回首屏均不再放圈
    var firstEntry by remember { mutableStateOf(true) }
    var buttonBounds by remember { mutableStateOf<Rect?>(null) }

    // 页间就是普通滑动切换(用户决定不做按钮展开转场); 每页的停留节奏由
    // GuidePage 的 BUTTON_ENABLE_DELAY_MS 控制, 防止狂点「继续」快速跳完
    fun goNext() {
        // 不再做全局防抖 —— 按钮自身的 500ms 门控就是防连点;
        // 叠加 2s 防抖会出现"按钮已亮起却吞点击好几秒"的假死
        firstEntry = false
        step += 1
    }

    // transitToPrevious: OOBE 内返回=回上一步; 回到首屏后不再放圆环
    BackHandler(enabled = step > 0) {
        if (step == 1) firstEntry = false
        step -= 1
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.surface)
    ) {
        val rootH = constraints.maxHeight.toFloat()

        // 页间翻页: 纯滑动(旧页淡出+视差会把辉光页洗白, 显得不协调), 双页同速对滑
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                if (targetState > initialState)
                    slideInHorizontally(tween(PAGE_SLIDE_MS, easing = SMOOTH)) { it } togetherWith
                        slideOutHorizontally(tween(PAGE_SLIDE_MS, easing = SMOOTH)) { -it }
                else
                    slideInHorizontally(tween(PAGE_SLIDE_MS, easing = SMOOTH)) { -it } togetherWith
                        slideOutHorizontally(tween(PAGE_SLIDE_MS, easing = SMOOTH)) { it }
            },
            label = "oobe",
        ) { target ->
            when (pages[target]) {
                OobePage.Splash -> SplashStep(
                    glowActive = glowActive,
                    blurGlass = blurGlass,
                    rootHeightPx = rootH,
                    admission = firstEntry,
                    entryAnim = firstEntry,
                    onExpand = { goNext() },
                )

                // 每页渲染自己的内容(必须按页分派 —— 曾因共享 movable 内容把
                // 权限页渲染成了第二个「本次更新」)
                OobePage.Welcome -> WelcomeStep(onBack = { step -= 1 }, onNext = { goNext() })
                OobePage.Permission -> PermissionStep(onBack = { step -= 1 }, onNext = { goNext() })
                OobePage.KeepAlive -> KeepAliveStep(onBack = { step -= 1 }, onNext = { goNext() })
                OobePage.Basic -> BasicStep(settings = settings, onBack = { step -= 1 }, onNext = { goNext() })
                OobePage.Done -> DoneStep(glowActive = glowActive, blurGlass = blurGlass, onDone = onDone)
            }
        }
    }
}

/**
 * 首屏 —— provision_startup_layout + StartupFragment + AnimHelper:
 * 权重 30%/90dp logo/40%(字标顶 + 圆钮底)/20%；
 * 入场 = startPageLogoAnim(logo 与字标同规格: scale 0.5→0.95 sinOut(440)→1.0 cubicOut(700),
 * alpha 延迟 60ms) + startPageBtnAnim(scale 0.9→1 + alpha, cubicOut(450ms) 延迟 1340ms)；
 * 2500ms displayOsAndoDelay 兜底强制可见可点。admission=首入放圆环(needAdmission(true))。
 */
@Composable
private fun SplashStep(
    glowActive: Boolean,
    blurGlass: Boolean,
    rootHeightPx: Float,
    admission: Boolean,
    entryAnim: Boolean,
    onExpand: () -> Unit,
) {
    // 字标组中心相对屏幕高度 → uCircleYOffset(setCircleYOffsetWithView)
    var wordmarkFrac by remember { mutableStateOf(0.345f) }

    GlowCanvas(
        modifier = Modifier.fillMaxSize(),
        active = glowActive,
        admission = admission,
        circleCenterFrac = wordmarkFrac,
    )

    // —— 入场动画（lite 降级: 不播, 全部落位）——
    val logoScale = remember { Animatable(if (entryAnim && glowActive) 0.5f else 1f) }
    val logoAlpha = remember { Animatable(if (entryAnim && glowActive) 0f else 1f) }
    val btnScale = remember { Animatable(if (entryAnim && glowActive) 0.9f else 1f) }
    val btnAlpha = remember { Animatable(if (entryAnim && glowActive) 0f else 1f) }
    var buttonReady by remember { mutableStateOf(!entryAnim || !glowActive) }

    LaunchedEffect(Unit) {
        if (!entryAnim || !glowActive) return@LaunchedEffect
        coroutineScope {
            launch { logoAlpha.animateTo(1f, tween(300, delayMillis = 60)) }
            launch {
                logoScale.animateTo(0.95f, tween(440, easing = SIN_OUT))
                logoScale.animateTo(1f, tween(700, easing = CUBIC_OUT))
            }
            launch {
                delay(BUTTON_IN_DELAY_MS)
                // scale 与 alpha 必须并行（同 logo 写法）: 若串行, 缩放期 alpha=0 展开全程隐形
                coroutineScope {
                    launch { btnScale.animateTo(1f, tween(BUTTON_IN_DUR_MS, easing = CUBIC_OUT)) }
                    launch { btnAlpha.animateTo(1f, tween(BUTTON_IN_DUR_MS, easing = CUBIC_OUT)) }
                }
                buttonReady = true
            }
        }
    }
    // displayOsAndoDelay: 2500ms 强制恢复, 防动画回调丢失导致按钮永久点不动
    LaunchedEffect(Unit) {
        delay(DISPLAY_OS_ANDO_MS)
        logoAlpha.snapTo(1f)
        logoScale.snapTo(1f)
        btnAlpha.snapTo(1f)
        btnScale.snapTo(1f)
        buttonReady = true
    }

    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.weight(0.30f))

        // logo 90dp（与字标同一套 scale/alpha —— startPageLogoAnim 同时作用两个 view）
        MjLogoIcon(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(110.dp)
                .graphicsLayer {
                    scaleX = logoScale.value
                    scaleY = logoScale.value
                    alpha = logoAlpha.value
                },
        )

        Box(
            modifier = Modifier
                .weight(0.40f)
                .fillMaxWidth(),
        ) {
            // 字标(logo_image_wrapper): 顶对齐 + marginTop 20dp, 深蓝紫(#3939AB 取样),
            // Black 字重(用户反馈 Bold 太细)
            Text(
                text = "MJ 彩蛋",
                color = WORDMARK_INDIGO,
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                maxLines = 3,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 20.dp)
                    .onGloballyPositioned {
                        val root = it.boundsInRoot()
                        if (root.height > 0f && rootHeightPx > 0f) {
                            wordmarkFrac = root.center.y / rootHeightPx
                        }
                    }
                    .graphicsLayer {
                        scaleX = logoScale.value
                        scaleY = logoScale.value
                        alpha = logoAlpha.value
                    },
            )

            // 圆钮 70dp(next_layout 4.5dp padding), 玻璃暗紫(#33356E 取样)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(4.5.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(70.dp)
                        .graphicsLayer {
                            scaleX = btnScale.value
                            scaleY = btnScale.value
                            alpha = btnAlpha.value
                        }
                        .clip(CircleShape)
                        .background(if (blurGlass) BUTTON_INDIGO else BTN_LITE)
                        .clickable(enabled = buttonReady, onClick = onExpand),
                    contentAlignment = Alignment.Center,
                ) {
                    ArrowIcon()
                }
            }
        }

        Spacer(Modifier.weight(0.20f))
    }
}

/** MJ logo: 桌面启动图标原图(已裁掉自适应前景的透明安全边), 与桌面图标一致。
 *  ic_launcher_foreground.png 是 432×432 画布只有中间 256 可见 —— 直接用会比
 *  指定尺寸小一圈(实测 140dp 只渲染 66dp), 必须用裁好的 oobe_logo.png。 */
@Composable
private fun MjLogoIcon(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.oobe_logo),
        contentDescription = null,
        modifier = modifier,
    )
}

/** provision_icon_arrow 31×22 viewport 路径逐点照抄, 落位 29×20dp。 */
@Composable
private fun ArrowIcon() {
    Canvas(Modifier.size(29.dp, 20.dp)) {
        val sx = size.width / 31f
        val sy = size.height / 22f
        val path = Path().apply {
            moveTo(29.7773f * sx, 11.9345f * sy)
            cubicTo(30.0173f * sx, 11.6945f * sy, 30.1371f * sx, 11.3798f * sy, 30.1366f * sx, 11.0651f * sy)
            cubicTo(30.1406f * sx, 10.746f * sy, 30.0209f * sx, 10.4256f * sy, 29.7774f * sx, 10.182f * sy)
            lineTo(20.9212f * sx, 1.3259f * sy)
            cubicTo(20.4421f * sx, 0.8468f * sy, 19.6652f * sx, 0.8468f * sy, 19.1861f * sx, 1.3259f * sy)
            cubicTo(18.707f * sx, 1.8051f * sy, 18.707f * sx, 2.5819f * sy, 19.1861f * sx, 3.0611f * sy)
            lineTo(25.7778f * sx, 9.6528f * sy)
            lineTo(1.6739f * sx, 9.6527f * sy)
            cubicTo(0.9532f * sx, 9.6527f * sy, 0.3689f * sx, 10.237f * sy, 0.3689f * sx, 10.9577f * sy)
            cubicTo(0.3689f * sx, 11.6785f * sy, 0.9532f * sx, 12.2627f * sy, 1.6739f * sx, 12.2627f * sy)
            lineTo(25.9788f * sx, 12.2627f * sy)
            lineTo(19.186f * sx, 19.0555f * sy)
            cubicTo(18.7069f * sx, 19.5346f * sy, 18.7069f * sx, 20.3115f * sy, 19.186f * sx, 20.7906f * sy)
            cubicTo(19.6652f * sx, 21.2698f * sy, 20.442f * sx, 21.2698f * sy, 20.9212f * sx, 20.7906f * sy)
            close()
        }
        drawPath(path, Color.White)
    }
}

/** 返回箭头 = ?android:homeAsUpIndicator: 「←」横杆箭头, 40dp。 */
@Composable
private fun BackIcon() {
    val color = MiuixTheme.colorScheme.onSurface
    Canvas(Modifier.size(40.dp)) {
        val u = size.minDimension / 100f
        val stroke = Stroke(width = 5f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val path = Path().apply {
            moveTo(74f * u, 50f * u)
            lineTo(28f * u, 50f * u)
            moveTo(45f * u, 33f * u)
            lineTo(28f * u, 50f * u)
            lineTo(45f * u, 67f * u)
        }
        drawPath(path, color, style = stroke)
    }
}

/** 预览图标 —— HyperCeiler provision_service_state/provision_basic_settings vector 逐字照抄 */
@Composable
private fun PreviewImage(resId: Int) {
    Image(
        painter = painterResource(resId),
        contentDescription = null,
        modifier = Modifier.size(70.dp),
    )
}

/**
 * 向导页外壳 —— provision_detail_layout + provision_actionbar + GroupButtons：
 * 顶部 40dp 返回钮(actionbar marginTop 50dp) → 70dp 居中线描预览图标 → 居中标题 32sp
 * (minHeight 42dp, 35dp 水平) → 居中副标题 14sp(tertiary) → 内容 → 底部「继续」
 * 50dp 圆角 16dp(max 336dp, 底距 44dp)。进页按钮 1000ms 后才可点(delayEnableButton)。
 */
@Composable
private fun GuidePage(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    onBack: () -> Unit,
    nextEnabled: Boolean = true,
    onNext: () -> Unit,
    onSkip: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    // delayEnableButton: 进页后 1000ms 按钮置灰, 防转场途中连点
    var enableDelayDone by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(BUTTON_ENABLE_DELAY_MS)
        enableDelayDone = true
    }
    val canNext = nextEnabled && enableDelayDone

    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        // actionbar: marginTop 50dp + paddingVertical 8dp
        // clip 成圆形再接点击 —— 否则水波纹按 40dp 方形可点区渲染, 触摸时出现方形阴影
        Box(
            Modifier
                .padding(start = 20.dp, top = 8.dp)
                .size(40.dp)
                .clip(CircleShape)
                .clickable(onClick = onBack),
        ) { BackIcon() }

        Spacer(Modifier.height(36.dp))   // provision_space_between_actionbar_title

        // preview_image 70dp
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 10.dp, bottom = 8.dp),
        ) { icon() }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 35.dp, vertical = 0.dp),
        ) {
            Text(
                text = title,
                color = MiuixTheme.colorScheme.onSurface,
                fontSize = 32.sp,
                lineHeight = 42.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 42.dp),
            )
            Spacer(Modifier.height(4.dp))
            // 副标题逐行渲染(整段渲染在窄宽下会出现逐字换行), 行间自然衔接
            subtitle.split('\n').forEach { line ->
                Text(
                    text = line,
                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(Modifier.height(24.dp))   // 小字与下方操作区拉开距离

        content()

        Spacer(Modifier.weight(1f))

        // 次级动作「暂不设置」(文本按钮, 紧挨主按钮上方), 与主按钮共用进页延时门控
        if (onSkip != null) {
            Box(
                Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "暂不设置",
                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .graphicsLayer { alpha = if (enableDelayDone) 1f else 0.5f }
                        .clickable(enabled = enableDelayDone, onClick = onSkip)
                        .padding(horizontal = 32.dp, vertical = 10.dp),
                )
            }
        }

        // 不可点=半透明蓝(置灰语义), 可点=全亮 —— 外观与 clickable 严格同帧翻转
        ProvisionButton(
            label = "继续",
            enabled = canNext,
            onClick = onNext,
        )
    }
}

/**
 * 底部主按钮 —— GroupButtons 主按钮观感(#3482FF, 50dp, 圆角 16dp, 白字 17sp,
 * max 336dp, 底距 44dp)。按钮本体不画禁用透明度 —— 否则"禁用 0.5→启用 1.0"
 * 会和外层淡入叠加出跳变(完成页「开始使用」踩过这个坑)。
 */
@Composable
private fun ProvisionButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    dark: Boolean = false,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 0.dp)
            .padding(bottom = 44.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 336.dp)
                .fillMaxWidth()
                .height(50.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (dark) Color(0x99000000) else PROVISION_BLUE)
                // 禁用 = 半透明蓝(HALF_ALPHA, 优雅的置灰); 可用 = 全亮
                // 与 clickable 严格同帧翻转 —— 不可点绝不显示可用态
                .graphicsLayer { alpha = if (enabled) 1f else 0.5f }
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/**
 * 权限设置页 —— PermissionSettingsActivity/Fragment:
 * 单门控范式: 无障碍服务未连通时「继续」禁用 + alpha 0.5(setAllowNext), 通后自动亮起。
 */
@Composable
private fun PermissionStep(onBack: () -> Unit, onNext: () -> Unit) {
    val context = LocalContext.current
    val a11y by produceState(initialValue = false to false) {
        while (true) {
            value = MjAccessibilityService.isConnected() to
                    MjAccessibilityService.isEnabledInSettings(context)
            delay(1000)
        }
    }
    val (connected, enabledInSettings) = a11y

    GuidePage(
        title = "权限设置",
        subtitle = "开启无障碍后彩蛋才能收事件",
        icon = { PreviewImage(R.drawable.oobe_ic_permission) },
        onBack = onBack,
        nextEnabled = connected || enabledInSettings,
        onNext = onNext,
        onSkip = onNext,   // 暂不设置: 跳过配置直接下一步
    ) {
        // miuix 标准行(Card + BasicComponent), 尾部 未开=右箭头(提示可点)/已开=对勾
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        ) {
            StatusRow(
                title = "无障碍服务",
                checked = connected || enabledInSettings,
                summary = if (connected) "已连接 正在收事件" else "点按跳转系统设置",
                showArrowWhenOff = true,
                onClick = { MjAccessibilityService.openAccessibilitySettings(context) },
            )
        }

        // 新手分步引导: 没用过无障碍的人照着三步就能开(各家 ROM 入口叫法都点了)
        Spacer(Modifier.height(4.dp))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                listOf(
                    "在「已下载的应用」或「已安装的服务」里打开开关",
                    "弹窗提示时选择允许",
                ).forEach { line ->
                    Text(
                        text = line,
                        color = MiuixTheme.colorScheme.onSurfaceContainer,
                        fontSize = MiuixTheme.textStyles.body2.fontSize,
                        lineHeight = 21.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                    )
                }
            }
        }
    }
}

/**
 * 欢迎页 —— 引导第二步(全新安装与版本更新重走都会经过)。
 * 用户决定: OOBE 不放更新日志, 这里只放一句欢迎 + 三步预告。
 */
@Composable
private fun WelcomeStep(onBack: () -> Unit, onNext: () -> Unit) {
    GuidePage(
        title = "欢迎使用 MJ 彩蛋",
        subtitle = "接下来三步就好\n全程只读 随时可改",
        icon = { MjLogoIcon(Modifier.size(70.dp)) },
        onBack = onBack,
        onNext = onNext,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                listOf(
                    "在聊天输入框发送 mj 即刻上演蜘蛛侠动画",
                    "支持 微信、QQ、钉钉、抖音 的聊天输入框",
                    "彩蛋只读监听 不会替你打字或发消息",
                ).forEach { line ->
                    Text(
                        text = "· $line",
                        color = MiuixTheme.colorScheme.onSurfaceContainer,
                        fontSize = MiuixTheme.textStyles.body2.fontSize,
                        lineHeight = 22.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                    )
                }
            }
        }
    }
}

/**
 * 保活设置页 —— 解决"息屏/后台久了收不到 mj"与"重装后事件被冻住"两类运行时问题。
 * 三行(自启动/忽略电池优化/锁定后台)均可点按跳转系统页, 状态 ON_RESUME 刷新;
 * 不门控流程(完成度无法可靠探测)。
 */
@Composable
private fun KeepAliveStep(onBack: () -> Unit, onNext: () -> Unit) {
    GuidePage(
        title = "保活设置",
        subtitle = "彩蛋要在后台收事件\n否则息屏久了会收不到 mj",
        icon = { LockIcon() },
        onBack = onBack,
        onNext = onNext,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        ) {
            KeepAliveRows()
        }
    }
}

/** 基础设置页 —— BasicSettingsActivity/Fragment: 偏好列表, 无门控。 */
@Composable
private fun BasicStep(settings: SettingsStore, onBack: () -> Unit, onNext: () -> Unit) {
    GuidePage(
        title = "基础设置",
        subtitle = "选择要监控的聊天应用 随时可以在「功能」页改",
        icon = { PreviewImage(R.drawable.oobe_ic_basic_settings) },
        onBack = onBack,
        onNext = onNext,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        ) {
            SwitchPreference(
                title = "微信", summary = "com.tencent.mm",
                checked = settings.targetWeChat,
                onCheckedChange = settings::updateTargetWeChat,
            )
            SwitchPreference(
                title = "QQ", summary = "com.tencent.mobileqq",
                checked = settings.targetQQ,
                onCheckedChange = settings::updateTargetQQ,
            )
            SwitchPreference(
                title = "钉钉", summary = "com.alibaba.android.rimet",
                checked = settings.targetDingTalk,
                onCheckedChange = settings::updateTargetDingTalk,
            )
            SwitchPreference(
                title = "抖音", summary = "com.ss.android.ugc.aweme",
                checked = settings.targetDouyin,
                onCheckedChange = settings::updateTargetDouyin,
            )
        }
    }
}

/**
 * 完成页 —— provision_congratulation_layout + CongratulationFragment:
 * 权重 18/40/0.2；辉光 start(false) 无圆环；logo 组 translationY 100px→0 + alpha
 * (quartOut 1500ms)；按钮 alpha sinOut(450ms) 延迟 1000ms, 2000ms 后才可点。
 * 点「开始使用」: logo 组 + 按钮 scale 1→0.8 spring(1.0, 0.36) + alpha sinOut(360ms) → 接力主页。
 * 字标 #3939AB / 状态字 #515497（真机取样）。
 */
@Composable
private fun DoneStep(glowActive: Boolean, blurGlass: Boolean, onDone: () -> Unit) {
    GlowCanvas(
        modifier = Modifier.fillMaxSize(),
        active = glowActive,
        admission = false,
        circleCenterFrac = 0.35f,
    )

    val entryAnim = glowActive
    val logoY = remember { Animatable(if (entryAnim) 100f else 0f) }
    val logoAlpha = remember { Animatable(if (entryAnim) 0f else 1f) }
    val btnAlpha = remember { Animatable(if (entryAnim) 0f else 1f) }
    val outScale = remember { Animatable(1f) }
    val outAlpha = remember { Animatable(1f) }
    var leaving by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!entryAnim) return@LaunchedEffect
        coroutineScope {
            launch {
                logoY.animateTo(0f, tween(1500, easing = QUART_OUT))
                logoAlpha.animateTo(1f, tween(1500, easing = QUART_OUT))
            }
            launch {
                delay(1000)                       // startBtnAnim setDelay(1000)
                btnAlpha.animateTo(1f, tween(450, easing = SIN_OUT))
            }
        }
    }

    if (leaving) {
        LaunchedEffect(Unit) {
            coroutineScope {
                launch {
                    // FolmeEase.spring(1.0f, 0.36f) = 阻尼 1.0(临界) / 响应 0.36s
                    outScale.animateTo(0.8f, spring(dampingRatio = 1f, stiffness = 305f))
                }
                launch {
                    outAlpha.animateTo(0f, tween(360, easing = SIN_OUT))
                }
            }
            onDone()
        }
    }

    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.weight(18f))

        Box(
            modifier = Modifier
                .weight(40f)
                .fillMaxWidth(),
        ) {
            // logo 组: 整组同一套入场/退场(startLogoAnim 作用在 wrapper 上)
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .graphicsLayer {
                        translationY = logoY.value          // Folme TRANSLATION_Y=100 原始像素
                        alpha = logoAlpha.value * outAlpha.value
                        scaleX = outScale.value
                        scaleY = outScale.value
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                MjLogoIcon(Modifier.size(110.dp))
                Spacer(Modifier.height(20.dp))
                Text(
                    text = "MJ 彩蛋",
                    color = WORDMARK_INDIGO,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                )
                Spacer(Modifier.height(30.dp))
                Text(
                    text = "设置完毕",
                    color = STATE_TEXT_INDIGO,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            // next_container: 底对齐 + marginBottom 44dp
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .graphicsLayer {
                        alpha = btnAlpha.value * outAlpha.value
                        scaleX = outScale.value
                        scaleY = outScale.value
                    },
            ) {
                // 按钮始终亮着(只受 leaving 约束) —— 淡入过程即可点, 无禁用跳变
                ProvisionButton(
                    label = "开始使用",
                    enabled = !leaving,
                    onClick = { leaving = true },
                    dark = true,
                )
            }
        }

        Spacer(Modifier.weight(0.2f))
    }
}

/**
 * 辉光画布 —— GlowPainter uniform 全表 + GlowController tickPingPong + RenderViewLayout:
 * 0.2× 分辨率居中渲染 + scale 5× + 黑底(-16777216)、uTime 从 0 起 ping-pong 2↔120
 * (只翻向不夹取, 照抄 tickPingPong)、uCircleYOffset = 0.5 - 圆心比例(setCircleYOffsetWithView)。
 * admission=false → needAdmission(false) 仅流动背景。shader 不可用(Android 11 等)
 * 时退化为**浅薰衣草静态渐变**(取自参考辉光色调) —— 字标/圆钮配色按浅底调, 兜底绝不能发黑。
 */
@Composable
private fun GlowCanvas(
    modifier: Modifier,
    active: Boolean,
    admission: Boolean,
    circleCenterFrac: Float,
) {
    if (!active) {
        Box(
            modifier.background(
                Brush.verticalGradient(
                    listOf(Color(0xFFDFE3FF), Color(0xFFCDCFFF), Color(0xFFB9C2FE))
                )
            )
        )
        return
    }
    val shader = remember {
        runCatching {
            RuntimeShader(GLOW_GLSL).apply {
            // GlowPainter 构造器的 uniform 全表 —— 数值照抄, 不许改
            setFloatUniform("uScale2", 0.82f)
            setFloatUniform("uSpeed2", 0.49f)
            setFloatUniform("uColorInMin", 0.3f)
            setFloatUniform("uColorInMax", 1.0f)
            setFloatUniform("uColorOutMin", 0.3f)
            setFloatUniform("uColorOutMax", 0.86f)
            setFloatUniform("uColorMidPoint", 0.47f)
            setFloatUniform("uUseOklab", 1.0f)
            setFloatUniform("uColorBlack", floatArrayOf(0.961f, 0.157f, 0.157f))  // 红橙(名不副实!)
            setFloatUniform("uColorMid", floatArrayOf(0.604f, 0.659f, 0.961f))    // 淡蓝紫
            setFloatUniform("uColorWhite", floatArrayOf(0.302f, 0.29f, 0.843f))   // 深蓝紫(名不副实!)
            setFloatUniform("uScale", 1.3f)
            setFloatUniform("uSpeed", 0.4f)
            setFloatUniform("uBrightnessInMin", 0.25f)
            setFloatUniform("uBrightnessInMax", 1.0f)
            setFloatUniform("uBrightnessOutMin", 0.25f)
            setFloatUniform("uBrightnessOutMax", 1.0f)
            setFloatUniform("uShowCircle", if (admission) 1.0f else 0.0f)
            setFloatUniform("uCircleThickness", 0.4f)
            setFloatUniform("uCircleFinalRadius", 1.0f)
            setFloatUniform("uCircleYOffset", 0.1f)
            setFloatUniform("uCircleSpeed", 0.9f)
            setFloatUniform("uCircleColorFreq", 1.0f)
            setFloatUniform("uCircleColorSpeed", 0.0f)
            setFloatUniform("uCircleEasing", 1.4f)
            setFloatUniform("uCircleAnimationOffset", 0.0f)
            setFloatUniform("uMaskDelay", 0.3f)
            setFloatUniform("uMaskThickness", 0.3f)
            setFloatUniform("uCircleScreenBlend", 1.0f)
            setFloatUniform("uCircleAddBlend", 0.04f)
            setFloatUniform("uCircleColorOffset", 0.25f)
            setFloatUniform("uCircleUVDistort", 0.0f)
            setFloatUniform("uColorToDistortWidthRatio", 0.6f)
            setFloatUniform("uDistortStartTime", 0.2f)
            setFloatUniform("uDistortEndTime", 0.3f)
            setFloatUniform("uDistortStart", 0.0f)
            setFloatUniform("uDistortEnd", 1.0f)
            setFloatUniform("uStripeFrequency", 0.0f)
            setFloatUniform("uStripeStrengthX", 0.0f)
            setFloatUniform("uStripeStrengthY", 0.0f)
            setFloatUniform("uStripeUVDistort", 0.0f)
        }
    }.getOrNull()   // shader 异常(如个别机型 AGSL 兼容性)时回退静态背景, 不崩溃
    }

    // GlowController: uTime 从 0 起步, ping-pong 2↔120（只翻向, 不夹取）
    val time = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        var dir = 1f
        var last = System.nanoTime()
        while (true) {
            val now = withFrameNanos { it }
            val dt = (now - last) / 1_000_000_000f
            last = now
            val v = time.value + dir * dt
            if (dir > 0f && v >= 120f) dir = -1f
            else if (dir < 0f && v <= 2f) dir = 1f
            time.snapTo(v)
        }
    }

    Box(modifier) {
        // RenderViewLayout 同款策略: 0.2× 居中 + scale 5× + 黑底
        Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .fillMaxWidth(0.2f)
                    .fillMaxHeight(0.2f)
                    .background(Color.Black)
                    .graphicsLayer {
                        scaleX = 5f
                        scaleY = 5f
                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                    }
                    .drawBehind {
                        val s = shader
                        if (s == null) {
                            drawRect(
                                Brush.verticalGradient(
                                    listOf(Color(0xFFDFE3FF), Color(0xFFCDCFFF), Color(0xFFB9C2FE))
                                )
                            )
                            return@drawBehind
                        }
                        s.setFloatUniform("uResolution", size.width, size.height)
                        s.setFloatUniform("uTime", time.value)
                        s.setFloatUniform("uCircleYOffset", 0.5f - circleCenterFrac)
                        drawRect(ShaderBrush(s))
                    }
            )
        }
    }
}
