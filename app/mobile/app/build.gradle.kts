import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

// 签名凭据不放进仓库: 优先读 ~/.gradle/keystore.properties, 其次项目根目录
val keystoreProperties = Properties().apply {
    val externalFile = File(System.getProperty("user.home"), ".gradle/keystore.properties")
    val projectFile = rootProject.file("keystore.properties")
    val file = when {
        externalFile.exists() -> externalFile
        projectFile.exists() -> projectFile
        else -> null
    }
    if (file != null) {
        file.inputStream().use { load(it) }
    }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    // Route : NavKey 需要 @Serializable, 由这个插件生成序列化器
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.lxithral.mjegg"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.lxithral.mjegg"
        // miuix-blur 0.9.3 自身声明 minSdk 33; 玻璃模糊本来也需要 API 33 的 RuntimeShader
        minSdk = 33
        targetSdk = 36
        versionCode = 16
        versionName = "1.0.15"
    }

    signingConfigs {
        create("release") {
            val storePath = keystoreProperties.getProperty("storeFile")
            if (storePath != null && File(storePath).exists()) {
                storeFile = File(storePath)
                storePassword = keystoreProperties.getProperty("storePassword") ?: ""
                keyAlias = keystoreProperties.getProperty("keyAlias") ?: ""
                keyPassword = keystoreProperties.getProperty("keyPassword") ?: ""
            }
        }
    }

    buildTypes {
        release {
            // 不开混淆: 无障碍服务由系统反射实例化, 且无法在本机真机验证 R8 结果, 保守起见关闭
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val rel = signingConfigs.getByName("release")
            if (rel.storeFile != null) {
                signingConfig = rel
            }
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    // miuix-nav 0.9.4 的 inline NavDisplay API 以 JVM 21 编译，应用侧必须对齐目标版本。
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // 动画 WebP / 音效不做压缩, 方便 ImageDecoder 与 MediaPlayer 直接读
    androidResources {
        noCompress += listOf("webp", "wav")
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        freeCompilerArgs.addAll("-Xskip-metadata-version-check")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)

    // miuix (小米 HyperOS 设计语言)
    implementation(libs.miuix.ui)
    implementation(libs.miuix.preference)
    implementation(libs.miuix.icons)
    implementation(libs.miuix.blur)
    // 导航栈: NavDisplay + 返回栈 + 滑动/视差转场
    implementation(libs.miuix.nav)

    // 预测性返回的隐藏 API 豁免
    implementation(libs.hiddenapibypass)

    // 纯 JVM 单元测试: 触发词规则与触发状态机(不需要真机)
    testImplementation(libs.junit)
}
