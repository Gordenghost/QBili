package com.qbili.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.qbili.ui.screen.login.LoginScreen
import com.qbili.ui.screen.opus.OpusScreen
import com.qbili.ui.screen.live.LiveScreen
import com.qbili.ui.screen.comment.CommentScreen
import com.qbili.ui.screen.article.ArticleScreen
import com.qbili.ui.screen.favorite.FavoriteScreen
import com.qbili.ui.screen.main.MainScreen
import com.qbili.ui.screen.messages.MessagesScreen
import com.qbili.ui.screen.placeholder.PlaceholderScreen
import com.qbili.ui.screen.search.SearchScreen
import com.qbili.ui.screen.settings.PushSettingsScreen
import com.qbili.ui.screen.settings.PushFilterManagementScreen
import com.qbili.domain.model.RecommendationFilterGroup
import com.qbili.ui.screen.settings.SettingsScreen
import com.qbili.ui.screen.settings.AboutScreen
import com.qbili.ui.screen.space.SpaceScreen
import com.qbili.ui.screen.video.VideoPlayerScreen
import com.qbili.ui.screen.watchlater.WatchLaterScreen

@Composable
fun QBiliNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = Route.MAIN,
        modifier = modifier,
    ) {
        composable(Route.MAIN) {
            MainScreen(
                onVideoClick = { video -> navController.navigate(Route.buildVideo(video.key)) },
                onSearchClick = { navController.navigate(Route.SEARCH) },
                onLoginClick = { navController.navigate(Route.LOGIN) },
                onSpaceClick = { mid -> navController.navigate(Route.buildSpace(mid)) },
                onFavoritesClick = { navController.navigate(Route.FAVORITES) },
                onWatchLaterClick = { navController.navigate(Route.WATCH_LATER) },
                onMessagesClick = { navController.navigate(Route.MESSAGES) },
                onSettingsClick = { navController.navigate(Route.SETTINGS) },
                onOpusClick = { navController.navigate(Route.buildOpus(it)) },
                onArticleClick = { navController.navigate(Route.buildArticle(it)) },
            )
        }

        composable(Route.SEARCH) {
            SearchScreen(
                onBack = { navController.popBackStack() },
                onVideoClick = { video -> navController.navigate(Route.buildVideo(video.key)) },
                onUserClick = { user -> navController.navigate(Route.buildSpace(user.mid)) },
                onLiveClick = { room -> navController.navigate(Route.buildLive(room.roomId)) },
                onSeasonClick = { season -> navController.navigate(Route.buildSeason(season.seasonId)) },
                onArticleClick = { article -> navController.navigate(Route.buildArticle(article.id)) },
            )
        }

        composable(Route.VIDEO) { entry ->
            // 路由参数可能是 BVxxx，也可能是 avNNN（移动端搜索结果只给 aid）
            val videoId = entry.arguments?.getString(Route.ARG_BVID).orEmpty()
            VideoPlayerScreen(
                videoId = videoId,
                onBack = { navController.popBackStack() },
                onAuthorClick = { mid -> navController.navigate(Route.buildSpace(mid)) },
                onCommentsClick = { aid -> navController.navigate(Route.buildComments(aid)) },
            )
        }

        composable(Route.COMMENTS) { entry ->
            val oid = entry.arguments?.getString(Route.ARG_COMMENT_OID)?.toLongOrNull() ?: 0L
            val type = entry.arguments?.getString(Route.ARG_COMMENT_TYPE)?.toIntOrNull() ?: 0
            CommentScreen(
                oid = oid,
                type = type,
                onBack = { navController.popBackStack() },
                onLoginClick = { navController.navigate(Route.LOGIN) },
            )
        }

        composable(Route.SPACE) { entry ->
            val mid = entry.arguments?.getString(Route.ARG_MID)?.toLongOrNull() ?: 0L
            SpaceScreen(
                mid = mid,
                onBack = { navController.popBackStack() },
                onLoginClick = { navController.navigate(Route.LOGIN) },
                onVideoClick = { video -> navController.navigate(Route.buildVideo(video.key)) },
                onAuthorClick = { authorMid -> if (authorMid != mid) {
                    navController.navigate(Route.buildSpace(authorMid))
                } },
                onOpusClick = { navController.navigate(Route.buildOpus(it)) },
                onArticleClick = { navController.navigate(Route.buildArticle(it)) },
            )
        }

        composable(Route.OPUS) { entry ->
            OpusScreen(
                id = entry.arguments?.getString(Route.ARG_OPUS_ID).orEmpty(),
                onBack = { navController.popBackStack() },
                onLoginClick = { navController.navigate(Route.LOGIN) },
                onCommentsClick = { type, oid ->
                    navController.navigate(Route.buildResourceComments(type, oid))
                },
                onAuthorClick = { navController.navigate(Route.buildSpace(it)) },
                onArticleClick = { navController.navigate(Route.buildArticle(it)) },
            )
        }

        composable(Route.LIVE) { entry ->
            val roomId = entry.arguments?.getString(Route.ARG_ROOM_ID)?.toLongOrNull() ?: 0L
            LiveScreen(
                roomId = roomId,
                onBack = { navController.popBackStack() },
                onAuthorClick = { mid -> navController.navigate(Route.buildSpace(mid)) },
            )
        }

        composable(Route.SEASON) { entry ->
            val seasonId = entry.arguments?.getString(Route.ARG_SEASON_ID).orEmpty()
            PlaceholderScreen(
                title = "番剧 / 影视",
                note = "PGC 播放（分集列表、正片鉴权）排在普通视频播放之后\n\n路由参数已就位：seasonId=$seasonId",
            )
        }

        composable(Route.ARTICLE) { entry ->
            val cvid = entry.arguments?.getString(Route.ARG_CVID)?.toLongOrNull() ?: 0L
            ArticleScreen(
                id = cvid,
                onBack = { navController.popBackStack() },
                onLoginClick = { navController.navigate(Route.LOGIN) },
                onCommentsClick = { navController.navigate(Route.buildResourceComments(12, it)) },
                onAuthorClick = { navController.navigate(Route.buildSpace(it)) },
                onArticleClick = { navController.navigate(Route.buildArticle(it)) },
            )
        }

        composable(Route.LOGIN) {
            LoginScreen(
                onBack = { navController.popBackStack() },
                // 登录成功后直接回到上一个页面，不要在返回栈里留下登录页
                onLoggedIn = { navController.popBackStack() },
            )
        }

        composable(Route.FAVORITES) {
            FavoriteScreen(
                onBack = { navController.popBackStack() },
                onLoginClick = { navController.navigate(Route.LOGIN) },
                onVideoClick = { video -> navController.navigate(Route.buildVideo(video.key)) },
            )
        }

        composable(Route.WATCH_LATER) {
            WatchLaterScreen(
                onBack = { navController.popBackStack() },
                onLoginClick = { navController.navigate(Route.LOGIN) },
                onVideoClick = { video -> navController.navigate(Route.buildVideo(video.key)) },
            )
        }

        composable(Route.MESSAGES) {
            MessagesScreen(
                onBack = { navController.popBackStack() },
                onLoginClick = { navController.navigate(Route.LOGIN) },
            )
        }

        composable(Route.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onPushSettingsClick = { navController.navigate(Route.PUSH_SETTINGS) },
                onAboutClick = { navController.navigate(Route.ABOUT) },
            )
        }

        composable(Route.ABOUT) {
            AboutScreen(onBack = { navController.popBackStack() })
        }

        composable(Route.PUSH_SETTINGS) {
            PushSettingsScreen(
                onBack = { navController.popBackStack() },
                onManageKeywordsClick = { navController.navigate(Route.buildPushFilter(it)) },
            )
        }
        RecommendationFilterGroup.entries.forEach { group ->
            composable(Route.buildPushFilter(group)) {
                PushFilterManagementScreen(group, onBack = { navController.popBackStack() })
            }
        }
    }
}
