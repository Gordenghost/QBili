# QBili（轻哔）

基于 **Kotlin + Jetpack Compose + Material 3** 的 B 站第三方 Android 客户端，采用单模块工程、手写依赖注入和分层架构。

> **非官方项目**：本项目与哔哩哔哩及其关联公司不存在隶属、合作或授权关系。功能仍在持续开发，接口变更或平台风控可能导致部分功能不可用。使用前请阅读 [免责声明](DISCLAIMER.md)。

## 功能

- **首页推荐**：接入网页端推荐接口、分页加载、下拉刷新；再次点击首页可回顶，在顶部再次点击可刷新。
- **推荐屏蔽**：按标题关键词、Tag、频道和 UP 主过滤推荐；独立管理屏蔽词、搜索、添加和删除。长按视频可不再显示当前视频，不重载整个列表。
- **搜索与登录**：多类型搜索；扫码、短信、密码及 Cookie 导入登录。
- **视频播放**：Media3 播放、画质／编码选择、分 P、弹幕、倍速、手势操作和定时关闭。
- **视频互动**：点赞、收藏、投币、关注、稍后再看和评论／回复等；部分能力受登录方式与接口权限限制。
- **UP 主空间**：个人资料、动态、视频投稿和独立图文栏目。
- **图文与专栏**：图文／专栏阅读、图片与富文本解析、评论、点赞和收藏。
- **其他模块**：热门／排行榜、收藏夹、稍后再看、私信和直播。

上述模块均有代码实现，但不代表与官方客户端完全一致，也不保证所有账号、内容和网络环境下均可用。

### 推荐屏蔽的边界

“不感兴趣”、标题、Tag 和频道屏蔽在本机过滤推荐，不会提交到 B 站官方推荐算法。通过菜单拉黑 UP 会同步到 B 站账号；在设置中移除本地 UP 屏蔽不会自动取消账号拉黑。

## 开发环境

| 项目 | 版本／要求 |
| --- | --- |
| Android | Android 8.0（API 26）及以上 |
| Compile / Target SDK | 35 |
| JDK | 17 |
| Gradle | 8.10.2 |
| Android Gradle Plugin | 8.8.2 |
| Kotlin | 2.1.0 |

安装 Android SDK Platform 35，并在 Android Studio 中配置 Gradle JDK 为 17，或将本机 `JAVA_HOME` 指向 JDK 17。SDK 路径可通过 Android Studio 自动生成的 `local.properties` 或 `ANDROID_HOME` 配置；不要提交自己的 `local.properties`。

## 构建与测试

克隆仓库并进入项目目录：

```bash
git clone https://github.com/Gordenghost/QBili.git
cd QBili
```

### Gradle Wrapper

macOS / Linux：

```bash
./gradlew testDebugUnitTest --console=plain
./gradlew assembleDebug --console=plain
```

Windows PowerShell：

```powershell
.\gradlew.bat testDebugUnitTest --console=plain
.\gradlew.bat assembleDebug --console=plain
```

Wrapper 使用官方 Gradle 8.10.2 分发包，并配置 SHA-256 校验。若网络超时或本地 Wrapper 缓存异常，可直接使用自行安装的 **Gradle 8.10.2**，不要为绕过下载问题取消校验。

### 本地 Gradle 备用方式

已将 JDK 17 和 Gradle 8.10.2 配置到环境变量时：

```powershell
gradle compileDebugKotlin --console=plain
gradle testDebugUnitTest --console=plain
gradle assembleDebug --console=plain
```

也可以调用本机 Gradle 的完整路径，并通过 `-Dorg.gradle.java.home=本机JDK17目录` 指定 JDK。此类绝对路径仅用于本机命令或用户级 Gradle 配置，不写入仓库的 `gradle.properties`。

### APK 与版本号

- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`。
- Debug 包名：`com.qbili.debug`；通常只能覆盖包名与签名均相同的旧版。不同开发者生成的 debug 签名可能不同，不能保证相互覆盖安装。
- Release 尚未配置签名，未签名产物不能直接用于安装分发；不要上传签名密钥或密码。
- `version.properties` 中的 `baseVersion` 是基础版本，`buildNumber` 在每次执行 `preBuild` 时自动递增。`versionName = baseVersion.buildNumber`，构建日志显示本次 `versionCode`。测试任务若触发 `preBuild`，也可能推进版本号。
- APK、构建目录、缓存和日志不会作为源码提交。仓库创建本身不代表已经发布 GitHub Release。

## 项目结构

```text
app/src/main/java/com/qbili/
├── ui/       Compose 页面、组件、导航与 ViewModel
├── domain/   领域模型及播放器等纯逻辑
├── data/     Repository、Retrofit 接口、DTO、分页与本地存储
├── core/     签名、错误处理、日志、格式化及协议解析
└── di/       AppContainer 手写依赖注入
app/src/test/ JVM 单元测试
```

网络层包含 WBI / App 请求签名、Cookie 管理、CSRF 注入以及风控错误处理。

## 隐私与安全注意事项

- Cookie 和访问令牌当前存于应用私有目录的本地配置中，**尚未采用额外的应用层加密存储**。不要在被 Root、受恶意软件控制或不可信的设备上保存重要账号登录态。
- 登录信息会用于向 B 站发送相应请求；验证码验证过程中也可能连接第三方验证服务。
- 诊断日志可能包含 UID、接口内容、访问地址，短信登录诊断尤其可能涉及敏感响应。**分享日志前务必人工检查并脱敏**，不要将原始日志、Cookie、密码、短信验证码、私信内容或令牌提交到公开 Issue。
- 本项目不承诺不会触发账号风控，不保证登录态持续有效；请自行评估账号与隐私风险。

## 反馈与贡献

欢迎提交 Issue 和 Pull Request。请提供版本、Android 版本、复现步骤和经过脱敏的必要日志。提交代码前运行相关 JVM 单元测试及 debug 构建，不要提交本机配置、账号凭据和打包产物。

新增 Retrofit 接口不要使用 Kotlin 默认参数；签名数据必须与实际发送的请求逐字节一致。可测试的纯逻辑应放入独立函数，并在 `app/src/test` 添加测试。

## 开源协议与第三方权利

本项目原创代码以 **GNU General Public License v3.0（GPL-3.0-only）** 授权，完整文本见 [LICENSE](LICENSE)。使用、修改和再分发时应遵守该协议；分发修改版本或二进制时应履行适用的源码提供、许可证与声明保留等义务。

GPL 授权不包含 B 站或内容创作者的商标、账号、视频、图文、音乐、图片、弹幕及其他第三方内容的使用权。第三方组件仍适用各自的许可证，参见 [第三方说明](THIRD_PARTY_NOTICES.md)。

**详细的非官方声明、第三方内容边界、账号风险和无担保说明见 [DISCLAIMER.md](DISCLAIMER.md)。免责声明不为 GPL 增加禁止商用或限制合法再分发等附加条件。**
