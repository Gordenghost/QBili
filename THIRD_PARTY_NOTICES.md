# 第三方说明

项目原创代码的 GPL-3.0-only 授权不改变第三方组件自身的许可证，也不授予任何在线内容或平台品牌的使用权。

## 随仓库提供的 Gradle Wrapper

`gradlew`、`gradlew.bat` 和 `gradle/wrapper/gradle-wrapper.jar` 来自 Gradle，保留原有许可声明。Wrapper JAR 内包含 `META-INF/LICENSE`；仓库另附 [Apache License 2.0 文本](LICENSES/Apache-2.0.txt)。

## 通过 Gradle 获取的依赖

主要依赖包括 Kotlin、AndroidX / Jetpack Compose / Material 3、Paging、DataStore、Media3、kotlinx.coroutines、kotlinx.serialization、OkHttp、Retrofit、Coil、ZXing 和 JUnit。坐标和版本以 `gradle/libs.versions.toml`、根构建脚本及 `app/build.gradle.kts` 为准。

这些依赖由 Gradle 获取，不以本项目原创代码名义重新授权。若分发包含第三方依赖的 APK 或其他产物，应核对所使用版本的许可证、版权与 NOTICE 要求，并保留适用声明；本说明不替代依赖自身的完整许可证。

## 网络服务与内容

B 站的接口、商标和在线内容，以及运行时加载的验证码服务和资源，均不因本仓库采用 GPL 而成为本项目授权的一部分。请分别遵守相应服务条款与内容授权，详细边界参见 [免责声明](DISCLAIMER.md)。
