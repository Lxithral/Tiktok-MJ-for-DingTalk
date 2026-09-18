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
}

android {
    namespace = "com.lxithral.mjegg"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.lxithral.mjegg"
        // miuix-blur 0.9.3 自身声明 minSdk 33; 玻璃模糊本来也需要 API 33 的 RuntimeShader
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
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
        jvmTarget.set(JvmTarget.JVM_17)
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
}
