# QBili（轻哔）

B站第三方安卓客户端。单模块 Kotlin + Jetpack Compose（Material3），手写 DI（`di/AppContainer.kt`），
分层为 `ui / domain / data / core`，网络层含 WBI 签名、App(appkey) 签名、CSRF 注入与 Gaia 风控闭环。

## 构建与测试

Gradle wrapper 缓存损坏且官方源下载超时，**直接用本地安装的 Gradle 8.10.2**
（AGP 8.8.2 的最低要求即 8.10.2，兼容）：

```
E:\environment\gradle-8.10.2\bin\gradle.bat -Dorg.gradle.java.home=E:/environment/java_env/jdk-17 compileDebugKotlin --console=plain -q
E:\environment\gradle-8.10.2\bin\gradle.bat -Dorg.gradle.java.home=E:/environment/java_env/jdk-17 testDebugUnitTest --console=plain
E:\environment\gradle-8.10.2\bin\gradle.bat -Dorg.gradle.java.home=E:/environment/java_env/jdk-17 assembleDebug --console=plain
```

共享的 `gradle.properties` 不记录本机绝对路径。使用 JDK 17：本机命令通过
`-Dorg.gradle.java.home` 指定目录，其他环境可配置 `JAVA_HOME` 或 Android Studio 的 Gradle JDK。

## 打包约定（重要）

1. **每次对话结束前必须执行一次 `assembleDebug` 打包**，让用户拿到包含本次修改的 APK。
   - 输出路径：`app/build/outputs/apk/debug/app-debug.apk`
   - 打包时主动告知用户版本号（构建日志里 `bumpBuildNumber` 任务会打印
     「本次 versionCode=N」）。
2. **不要手动改 `version.properties`**：`buildNumber`（即 versionCode）在每次构建的
   `preBuild` 阶段自动 +1，`versionName = baseVersion.buildNumber`，
   因此每个新包都能覆盖安装旧包。
3. release 构建未配置签名（产出未签名 APK 无法安装），日常分发一律用 debug 包；
   debug 的包名带 `.debug` 后缀，只能覆盖安装同为 debug 的旧版。

## 开发约定

- 每次完成新的代码或文档改动后，运行相关测试并执行 `assembleDebug`，随后提交并推送到 GitHub 的当前分支；不要提交本机配置、账号凭据、日志或 APK。构建自动更新的 `version.properties` 可随本次改动提交，不要手动修改。
- 仅推送源码不会触发应用更新提示；检查更新读取 GitHub 已发布的正式 Release。除非用户要求发布新版本，不自动创建 Release 或上传 APK。
- 中文注释解释「为什么」而不是复述代码。
- Retrofit 接口方法不写 Kotlin 默认参数值（DefaultImpls 会导致解析异常），
  固定参数由 Repository 传入。
- 签名用的 query/表单必须与实际发出的请求逐字节一致（WBI 与 AppSign 均如此）。
- 纯逻辑抽成可 JVM 单测的函数；测试位于 `app/src/test`。
- B站接口错误码语义见 `core/BiliApiException.kt` 与 `core/ErrorMessages.kt`
  （风控响应 code 恒为 0、只把 result 换成 v_voucher，切勿当成功处理）。

## 路线图（代码内阶段标注）

已完成：首页推荐流、搜索全类型、四方式登录（扫码/App短信/密码/Cookie导入）、极验组件。
待做占位：第6阶段视频播放（Media3 + playurl + 弹幕渲染，路由参数已就位）→
7 稍后再看 → 8 评论 → 9 空间/收藏/动态 → 10 排行榜 → 11 私信 → 12 直播。
