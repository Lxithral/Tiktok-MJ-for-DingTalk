package com.lxithral.mjegg.egg

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Animatable2
import android.graphics.drawable.Drawable
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import com.lxithral.mjegg.platform.SettingsStore
import java.io.File
import java.io.FileOutputStream

/**
 * 全屏透明播放层 —— 桌面版 `mjegg/overlay.py` 的手机版对应物。
 *
 * 关键点:
 * - 用 **TYPE_ACCESSIBILITY_OVERLAY** 加窗: 无障碍服务专属窗口类型,
 *   **不需要** SYSTEM_ALERT_WINDOW("显示在其他应用上层")权限, 也不需要悬浮窗授权弹窗。
 * - 三个 flag 保证"看得见但碰不到": NOT_FOCUSABLE(不抢焦点) + NOT_TOUCHABLE(点击穿透)。
 * - 动画素材是带 alpha 的动画 WebP, 用 ImageDecoder 直接解码成 AnimatedImageDrawable。
 * - 两段动画(坠落/荡绳)交替播放, 并按素材构图分别贴右上角 / 左上角。
 */
class EggOverlay(private val service: AccessibilityService) {

    private val handler = Handler(Looper.getMainLooper())
    private var root: View? = null
    private var player: MediaPlayer? = null
    private var anim: AnimatedImageDrawable? = null
    private var altIndex = 0

    private val dismissRunnable = Runnable { dismiss() }

    fun isShowing(): Boolean = root != null

    /** 播放一次彩蛋。返回 false 表示素材/窗口不可用。 */
    fun play(): Boolean {
        val settings = SettingsStore.get(service)
        dismiss()

        val clip = CLIPS[altIndex]
        altIndex = (altIndex + 1) % CLIPS.size

        val drawable = try {
            ImageDecoderCompat.decode(service, clip.file)
        } catch (t: Throwable) {
            Log.e(TAG, "解码动画失败: ${clip.file}", t)
            return false
        } ?: return false

        val wm = service.getSystemService(WindowManager::class.java) ?: return false
        val bounds = wm.currentWindowMetrics.bounds
        val screenH = bounds.height()

        val iw = drawable.intrinsicWidth.takeIf { it > 0 } ?: 1072
        val ih = drawable.intrinsicHeight.takeIf { it > 0 } ?: 2352
        val ratio = (settings.overlayHeight.coerceIn(30, 120)) / 100f
        val targetH = (screenH * ratio).toInt().coerceAtLeast(1)
        val targetW = (targetH.toFloat() * iw / ih).toInt().coerceAtLeast(1)

        val image = ImageView(service).apply {
            setImageDrawable(drawable)
            scaleType = ImageView.ScaleType.FIT_XY
        }
        val container = FrameLayout(service).apply {
            addView(
                image,
                FrameLayout.LayoutParams(targetW, targetH).apply {
                    gravity = if (clip.anchorRight) Gravity.END or Gravity.TOP
                    else Gravity.START or Gravity.TOP
                }
            )
        }

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        return try {
            wm.addView(container, lp)
            root = container

            // ImageDecoderCompat.decode 保证返回 AnimatedImageDrawable
            anim = drawable
            drawable.repeatCount = 0 // 播一遍
            drawable.registerAnimationCallback(object : Animatable2.AnimationCallback() {
                override fun onAnimationEnd(d: Drawable?) {
                    handler.post { dismiss() }
                }
            })
            drawable.start()

            playSound(clip.sound, settings.volume / 100f)

            // 看门狗: 素材异常/回调不来时兜底回收
            handler.removeCallbacks(dismissRunnable)
            handler.postDelayed(dismissRunnable, WATCHDOG_MS)
            Log.i(TAG, "开始播放 ${clip.file} (${targetW}x$targetH, 锚定${if (clip.anchorRight) "右上" else "左上"})")
            settings.bumpTriggerCount()
            true
        } catch (t: Throwable) {
            Log.e(TAG, "加窗失败", t)
            dismiss()
            false
        }
    }

    fun dismiss() {
        handler.removeCallbacks(dismissRunnable)
        try {
            anim?.stop()
        } catch (_: Throwable) {
        }
        anim = null
        try {
            player?.stop()
            player?.release()
        } catch (_: Throwable) {
        }
        player = null
        root?.let { v ->
            try {
                service.getSystemService(WindowManager::class.java)?.removeView(v)
            } catch (_: Throwable) {
            }
        }
        root = null
    }

    // ---------- 音效 ----------
    private fun playSound(assetName: String, volume: Float) {
        if (volume <= 0f) return
        try {
            val f = ensureSoundFile(assetName) ?: return
            player = MediaPlayer().apply {
                setDataSource(f.absolutePath)
                setVolume(volume, volume)
                setOnCompletionListener { mp ->
                    mp.release()
                    if (player === mp) player = null
                }
                prepare()
                start()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "播放音效失败: $assetName", t)
            player = null
        }
    }

    /** wav 从 assets 复制到 cacheDir 再用文件路径播放, 绕开 openFd 对压缩资源的限制。 */
    private fun ensureSoundFile(assetName: String): File? {
        val out = File(service.cacheDir, assetName)
        if (out.exists() && out.length() > 0) return out
        return try {
            service.assets.open(assetName).use { input ->
                FileOutputStream(out).use { fos -> input.copyTo(fos) }
            }
            out
        } catch (t: Throwable) {
            Log.w(TAG, "复制音效失败: $assetName", t)
            null
        }
    }

    private data class Clip(val file: String, val sound: String, val anchorRight: Boolean)

    companion object {
        private const val TAG = "MjEgg.Overlay"
        private const val WATCHDOG_MS = 8_000L

        // 与桌面版一致: 坠落贴右上角, 荡绳贴左上角(蛛丝悬挂点在画面左边界之外)
        private val CLIPS = listOf(
            Clip("mj-drop-alpha.webp", "mj-drop-alpha.wav", anchorRight = true),
            Clip("mj-swing-alpha.webp", "mj-swing-alpha.wav", anchorRight = false),
        )
    }
}

/** 把 ImageDecoder 的调用收在一处, 便于加 targetSize / 异常兜底。 */
private object ImageDecoderCompat {
    fun decode(service: AccessibilityService, file: String): AnimatedImageDrawable? {
        val source = android.graphics.ImageDecoder.createSource(service.assets, file)
        val d = android.graphics.ImageDecoder.decodeDrawable(source)
        return d as? AnimatedImageDrawable
    }
}
