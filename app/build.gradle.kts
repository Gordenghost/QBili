import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// ---- 版本号：baseVersion 手动维护，buildNumber 每次构建自动 +1 ----
val versionPropsFile = rootProject.file("version.properties")
val versionProps = Properties().apply {
    if (versionPropsFile.exists()) versionPropsFile.inputStream().use { load(it) }
}
val buildNumber = (versionProps.getProperty("buildNumber") ?: "1").trim().toInt()
val baseVersion = (versionProps.getProperty("baseVersion") ?: "0.1").trim()

/**
 * 构建结束后把 buildNumber 递增，于是下一个 APK 的 versionCode 必然不同。
 * 本次构建用的仍是配置阶段读到的 [buildNumber]，所以版本号是单调递增且不重复的。
 */
val bumpBuildNumber = tasks.register("bumpBuildNumber") {
    description = "递增 version.properties 中的 buildNumber"
    doLast {
        versionProps.setProperty("buildNumber", (buildNumber + 1).toString())
        versionPropsFile.outputStream().use {
            versionProps.store(it, "buildNumber 由构建脚本自动递增；baseVersion 手动维护")
        }
        logger.lifecycle("本次 versionCode=$buildNumber，下次将为 ${buildNumber + 1}")
    }
}
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(bumpBuildNumber) }

android {
    namespace = "com.qbili"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.qbili"
        minSdk = 26
        targetSdk = 35
        versionCode = buildNumber
        versionName = "$baseVersion.$buildNumber"
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/INDEX.LIST",
            )
        }
    }

    testOptions {
        unitTests {
            // 否则 android.util.Log 之类的框架调用会在 JVM 单测里直接抛
            // "Method not mocked"，把被测逻辑打断
            isReturnDefaultValues = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.addAll("-opt-in=kotlin.RequiresOptIn")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.coil.compose)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui)
    implementation(libs.media3.datasource.okhttp)

    // 登录二维码渲染（纯 Java 实现，不依赖 Android 框架）
    implementation(libs.zxing.core)

    testImplementation(libs.junit)
}
