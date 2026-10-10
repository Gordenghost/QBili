package com.qbili.di

import android.content.Context
import com.qbili.data.local.AccessTokenStore
import com.qbili.data.local.DeviceIdStore
import com.qbili.data.local.RecommendationFilterStore
import com.qbili.data.local.RecommendationSettingsStore
import com.qbili.data.local.SearchHistoryStore
import com.qbili.data.local.SeasonProgressStore
import com.qbili.core.QBiliLog
import com.qbili.data.remote.BiliNetwork
import com.qbili.data.remote.GitHubNetwork
import com.qbili.data.remote.api.AccountApi
import com.qbili.data.remote.api.ArticleApi
import com.qbili.data.remote.api.CommentApi
import com.qbili.data.remote.api.FavoriteApi
import com.qbili.data.remote.api.AppPassportApi
import com.qbili.data.remote.api.AppInteractionApi
import com.qbili.data.remote.api.AppSearchApi
import com.qbili.data.remote.api.FeedApi
import com.qbili.data.remote.api.AppFeedApi
import com.qbili.data.remote.api.DynamicApi
import com.qbili.data.remote.api.LiveApi
import com.qbili.data.remote.api.SpaceApi
import com.qbili.data.remote.api.RankingApi
import com.qbili.data.remote.api.GaiaApi
import com.qbili.data.remote.api.InteractionApi
import com.qbili.data.remote.api.MessageApi
import com.qbili.data.remote.api.OpusActionApi
import com.qbili.data.remote.api.PassportApi
import com.qbili.data.remote.api.PlayurlApi
import com.qbili.data.remote.api.SeasonApi
import com.qbili.data.remote.api.VideoApi
import com.qbili.data.remote.api.VideoTagApi
import com.qbili.data.remote.api.SearchApi
import com.qbili.data.remote.api.SuggestApi
import com.qbili.data.repository.AuthRepository
import com.qbili.data.repository.AppUpdateRepository
import com.qbili.data.repository.ArticleRepository
import com.qbili.data.repository.CommentRepository
import com.qbili.data.repository.FavoriteRepository
import com.qbili.data.repository.FeedRepository
import com.qbili.data.repository.DynamicRepository
import com.qbili.data.repository.LiveRepository
import com.qbili.data.repository.SpaceRepository
import com.qbili.data.repository.RankingRepository
import com.qbili.data.repository.GaiaRepository
import com.qbili.data.repository.InteractionRepository
import com.qbili.data.repository.MessageRepository
import com.qbili.data.repository.OpusInteractionRepository
import com.qbili.data.repository.DanmakuRepository
import com.qbili.data.repository.SearchRepository
import com.qbili.data.repository.VideoRepository
import com.qbili.data.repository.SeasonRepository
import com.qbili.domain.model.SeasonProgress
import com.qbili.data.session.GaiaTokenStore
import com.qbili.data.session.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 手写依赖容器，替代 Hilt。
 *
 * 选择手写而不是引入注解处理器：本项目是单模块应用，依赖图很浅，
 * 手写容器省掉 KSP/Hilt 的版本对齐与构建耗时，后续要换 Hilt 也只需替换这一个文件。
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    /** 与应用生命周期一致的 scope，用于登录态维护、退出登录这类不该被页面取消的任务 */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val network = BiliNetwork(appContext)
    private val gitHubNetwork by lazy { GitHubNetwork() }
    val appUpdateRepository: AppUpdateRepository by lazy {
        AppUpdateRepository(gitHubNetwork.releaseApi)
    }

    private val feedApi: FeedApi by lazy { network.apiRetrofit.create(FeedApi::class.java) }
    private val appFeedApi: AppFeedApi by lazy { network.appRetrofit.create(AppFeedApi::class.java) }
    private val articleApi: ArticleApi by lazy { network.apiRetrofit.create(ArticleApi::class.java) }
    private val videoTagApi: VideoTagApi by lazy { network.apiRetrofit.create(VideoTagApi::class.java) }
    private val dynamicApi: DynamicApi by lazy { network.apiRetrofit.create(DynamicApi::class.java) }
    private val liveApi: LiveApi by lazy { network.liveRetrofit.create(LiveApi::class.java) }
    private val spaceApi: SpaceApi by lazy { network.apiRetrofit.create(SpaceApi::class.java) }
    private val rankingApi: RankingApi by lazy { network.apiRetrofit.create(RankingApi::class.java) }
    private val searchApi: SearchApi by lazy { network.apiRetrofit.create(SearchApi::class.java) }
    private val accountApi: AccountApi by lazy { network.apiRetrofit.create(AccountApi::class.java) }
    private val gaiaApi: GaiaApi by lazy { network.apiRetrofit.create(GaiaApi::class.java) }
    private val suggestApi: SuggestApi by lazy { network.suggestRetrofit.create(SuggestApi::class.java) }
    private val passportApi: PassportApi by lazy {
        network.passportRetrofit.create(PassportApi::class.java)
    }
    private val appPassportApi: AppPassportApi by lazy {
        network.passportRetrofit.create(AppPassportApi::class.java)
    }
    private val appSearchApi: AppSearchApi by lazy {
        network.appRetrofit.create(AppSearchApi::class.java)
    }
    // playurl 与详情、弹幕都在 api.bilibili.com 上，且都不需要签名
    private val playurlApi: PlayurlApi by lazy { network.apiRetrofit.create(PlayurlApi::class.java) }
    private val videoApi: VideoApi by lazy { network.apiRetrofit.create(VideoApi::class.java) }
    private val seasonApi: SeasonApi by lazy { network.apiRetrofit.create(SeasonApi::class.java) }
    private val interactionApi: InteractionApi by lazy {
        network.apiRetrofit.create(InteractionApi::class.java)
    }
    private val commentApi: CommentApi by lazy { network.apiRetrofit.create(CommentApi::class.java) }
    private val messageApi: MessageApi by lazy { network.vcRetrofit.create(MessageApi::class.java) }
    private val opusActionApi: OpusActionApi by lazy { network.apiRetrofit.create(OpusActionApi::class.java) }
    private val favoriteApi: FavoriteApi by lazy { network.apiRetrofit.create(FavoriteApi::class.java) }
    // 点踩只有 App 端接口，走 app.bilibili.com（host 命中即自动签名）
    private val appInteractionApi: AppInteractionApi by lazy {
        network.appRetrofit.create(AppInteractionApi::class.java)
    }

    /** Gaia 风控凭据在多个 Repository 之间共享 */
    private val gaiaTokenStore = GaiaTokenStore()

    private val deviceIdStore by lazy { DeviceIdStore(appContext) }
    private val accessTokenStore by lazy { AccessTokenStore(appContext) }
    val hasAppRecommendationCredentials: Boolean get() = accessTokenStore.hasAccessKey

    val feedRepository: FeedRepository by lazy {
        FeedRepository(feedApi, videoTagApi, appFeedApi,
            accessKey = { accessTokenStore.accessKey }, buvid = { deviceIdStore.getOrCreate() })
    }
    val articleRepository: ArticleRepository by lazy { ArticleRepository(articleApi) }
    val dynamicRepository: DynamicRepository by lazy { DynamicRepository(dynamicApi) }
    val liveRepository: LiveRepository by lazy { LiveRepository(liveApi) }
    val spaceRepository: SpaceRepository by lazy { SpaceRepository(spaceApi) }
    val rankingRepository: RankingRepository by lazy { RankingRepository(rankingApi) }
    val searchRepository: SearchRepository by lazy {
        SearchRepository(searchApi, appSearchApi, suggestApi, gaiaTokenStore)
    }
    val gaiaRepository: GaiaRepository by lazy { GaiaRepository(gaiaApi, gaiaTokenStore) }
    val authRepository: AuthRepository by lazy {
        AuthRepository(
            passportApi = passportApi,
            appPassportApi = appPassportApi,
            accountApi = accountApi,
            cookieJar = network.cookieJar,
            deviceIdStore = deviceIdStore,
            accessTokenStore = accessTokenStore,
        )
    }
    val videoRepository: VideoRepository by lazy { VideoRepository(videoApi, playurlApi) }
    val seasonRepository: SeasonRepository by lazy { SeasonRepository(seasonApi) }
    val seasonProgressStore: SeasonProgressStore by lazy { SeasonProgressStore(appContext) }
    fun saveSeasonProgress(seasonId: Long, progress: SeasonProgress) {
        appScope.launch {
            runCatching { seasonProgressStore.save(seasonId, progress) }
                .onFailure { QBiliLog.w("SeasonProgress", "保存播放进度失败：${it.message}") }
        }
    }
    val danmakuRepository: DanmakuRepository by lazy { DanmakuRepository(videoApi) }
    val interactionRepository: InteractionRepository by lazy {
        InteractionRepository(interactionApi, appInteractionApi, accessTokenStore)
    }
    val commentRepository: CommentRepository by lazy { CommentRepository(commentApi) }
    val messageRepository: MessageRepository by lazy { MessageRepository(messageApi) }
    val opusInteractionRepository: OpusInteractionRepository by lazy {
        OpusInteractionRepository(opusActionApi) { network.cookieJar.csrf() }
    }
    val favoriteRepository: FavoriteRepository by lazy { FavoriteRepository(favoriteApi) }

    val searchHistoryStore: SearchHistoryStore by lazy { SearchHistoryStore(appContext) }
    val recommendationFilterStore: RecommendationFilterStore by lazy {
        RecommendationFilterStore(appContext)
    }
    val recommendationSettingsStore: RecommendationSettingsStore by lazy {
        RecommendationSettingsStore(appContext)
    }

    val sessionManager: SessionManager by lazy { SessionManager(authRepository, appScope) }

    /** 由 Application.onCreate 调用一次 */
    fun bootstrap() {
        sessionManager.bootstrap()
    }
}
