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

已有基础实现：首页推荐流、搜索全类型、四方式登录、极验、视频播放与弹幕、稍后再看、
评论／回复、空间／收藏／动态／图文专栏、热门／排行榜、私信、直播、关于／更新检查。
第13阶段番剧／影视（PGC）：详情、分集、正片／试看播放、追番与本机断点续播。
第14阶段观看历史／普通视频续播：账号历史分页与日期分组、单条删除／确认清空，
按账号隔离的本机普通视频记录、分 P 续播提示及默认关闭的账号进度上传开关。
番剧续播仍使用独立本机存储；直播历史进入当前直播间，不提供历史回放续播。
第15阶段历史搜索／筛选：标题、UP、分 P、BV／AV 号多关键词搜索，账号历史按内容类型筛选；
账号仅筛选已加载记录，搜索时关闭自动翻页，保留更早记录加载入口；本机搜索保持账号隔离。
切换账号重置搜索，切换来源保留关键词；清空确认始终说明删除当前来源全部历史。
第16阶段更多历史入口：专栏按 OID、文集文章按 CID 打开；直播按房间 OID 打开；
图文仅用明确 opus 业务 ID 或可信站内 /opus/ 链接，未知业务仅识别精确阅读／图文／直播链接。
URI 限定 HTTP(S)、精确域名和标准端口，无用户信息；删除仍使用 business_KID，不混用导航 ID。
历史搜索与筛选为普通列表首项，随列表滚走；紧凑单行输入框，顶栏搜索按钮回顶聚焦。
第17阶段私信体验／图片消息：会话缺少资料时并发有界补查昵称头像，失败标记 UID 回退；
双方头像可跳转空间、时间分组、文字复制、反向消息列表稳定加载更早记录、输入栏避让系统栏。
图片／自定义表情仅加载可信 B站图床，复用统一预览和保存；撤回内容隐藏，不展示原始 JSON。
账号变化取消旧请求，关闭／重开同一会话使用代次隔离，旧发送不能清除新草稿；不自动轮询或标记已读。
第18阶段私信分享卡片／通知：分享消息、视频／专栏推送、直播等其他分享显示标题、封面、摘要和赠言；
通知显示模块与最多三个站内按钮，系统提示单独居中显示；会话摘要与聊天共用纯逻辑解析。
明确类型才使用对应 AV／CV／season ID，动态 ID 不当作 opus ID，ep ID 不当作 season ID；
其他入口仅识别 HTTP(S)、精确站内域名与标准端口的完整路径，不自动解析短链或打开外链／客户端协议。
暂不支持的卡片保留说明，撤回隐藏所有跳转；仍只发送文字，不自动轮询或标记已读。
第19阶段热门完善：综合热门分页／下拉刷新、每周必看选期与期数／日期／主题搜索、入站必刷，
投稿排行榜支持全站与主要分区；榜单／每周／经典一次性加载，不伪造分页，不混用 PGC 分区 ID。
视频卡片按可用宽度布局，排名放封面角标；UP 名称跳转主页，精选理由独立显示，提供回顶按钮。
切换栏目／分区／期数取消并隔离旧请求；刷新失败保留当前列表，分页失败重试原页，不自动重试错误；
重复分页去重并停止无新增页，缺失列表视为异常而非成功无内容，风控按错误提示不绕过。
后续重点为播放互动完善和课程等更多内容类型支持。
图文／专栏／动态使用统一图片预览器（缩放／保存），图文复用安全 WebView 富文本排版；
仅未知正文保留外部完整阅读入口。评论与楼中楼头像、昵称支持跳转空间。
主题使用本机 DataStore 保存跟随系统／清爽白／深色选择，根页面统一应用配色与系统栏明暗；
图片预览保持黑色，保存提示通过 Scaffold 悬浮在系统安全区域内。
动态支持关注 UP 搜索选择与独立空间分页，账号切换取消旧请求；头像／昵称可跳转空间。
普通视频续播通过视频左侧按钮提供，不阻断播放；退出先停播清理，导航过渡解绑 TextureView 画面。
图文正文按非空段落内容解析，空图片／标题等占位字段不得遮蔽文本；黑白文字跟随阅读主题。
清爽白以灰色背景衬托纯白内容容器，默认 Card 对应的 surfaceContainerHighest 也必须为白色；
聚焦内容不得使用 surfaceVariant 灰底，灰色仅用于背景、图片占位和次要元素，选中状态用文字／图标点缀。
「我的」页面与 UP 主页上部资料、栏目入口使用 surface 白色，空间分区间隔保留灰色。
视频动态使用 16:9 封面、播放图标、视频类型标签和时长；图文图片仍独立点击预览。
转发动态不再扁平合并正文，original 保留原作者与内容；链接解析使用 linkedContent，
引用最多递归三层，原动态缺失／删除明确提示，补图文正文使用原图文 ID 而非转发 ID。
浅色多个白色容器对应的 onContainer 必须与 onSurface 相同，避免 contentColorFor 首次匹配变红；
默认正文与菜单为深色，动态昵称显式使用 onSurface，品牌色仅点缀操作与选中状态。
