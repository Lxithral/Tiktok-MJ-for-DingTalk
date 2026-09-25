package com.lxithral.mjegg

import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.SideEffect
import com.lxithral.mjegg.platform.SettingsStore
import com.lxithral.mjegg.ui.navigation.AppNavigation
import com.lxithral.mjegg.ui.theme.AppTheme
import org.lsposed.hiddenapibypass.HiddenApiBypass

/** 唯一 Activity: 主题包住一切, 二级页切换交给 miuix-nav 返回栈。 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val settings = SettingsStore.get(this)
        setContent {
            AppTheme(settings) {
                // 设置开关变更后立即把系统级预测性返回管线同步到当前 ApplicationInfo。
                SideEffect {
                    PredictiveBack.apply(applicationInfo, settings.predictiveBack)
                }
                AppNavigation(settings)
            }
        }
    }

    private object PredictiveBack {
        fun apply(appInfo: ApplicationInfo, enabled: Boolean) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
            runCatching {
                HiddenApiBypass.addHiddenApiExemptions(
                    "Landroid/content/pm/ApplicationInfo;->setEnableOnBackInvokedCallback"
                )
                val method = ApplicationInfo::class.java.getDeclaredMethod(
                    "setEnableOnBackInvokedCallback",
                    Boolean::class.javaPrimitiveType,
                )
                method.isAccessible = true
                method.invoke(appInfo, enabled)
            }
        }
    }
}
