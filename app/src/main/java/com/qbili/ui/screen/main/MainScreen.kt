package com.qbili.ui.screen.main

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.qbili.domain.model.VideoItem
import com.qbili.ui.navigation.TabRoute
import com.qbili.ui.screen.home.HomeScreen
import com.qbili.ui.screen.dynamic.DynamicScreen
import com.qbili.ui.screen.placeholder.PlaceholderScreen
import com.qbili.ui.screen.ranking.RankingScreen
import com.qbili.ui.screen.profile.ProfileScreen
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow

private data class TabItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

private val TABS = listOf(
    TabItem(TabRoute.HOME, "首页", Icons.Filled.Home, Icons.Outlined.Home),
    TabItem(TabRoute.RANKING, "热门", Icons.Filled.Whatshot, Icons.Outlined.Whatshot),
    TabItem(TabRoute.DYNAMIC, "动态", Icons.Outlined.Notifications, Icons.Outlined.Notifications),
    TabItem(TabRoute.PROFILE, "我的", Icons.Filled.Person, Icons.Outlined.Person),
)

@Composable
fun MainScreen(
    onVideoClick: (VideoItem) -> Unit,
    onSearchClick: () -> Unit,
    onLoginClick: () -> Unit,
    onSpaceClick: (Long) -> Unit,
    onFavoritesClick: () -> Unit,
    onWatchLaterClick: () -> Unit,
    onMessagesClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onOpusClick: (String) -> Unit,
    onArticleClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabNavController = rememberNavController()
    val backStackEntry by tabNavController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val homeReselections = remember {
        MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    }

    Scaffold(
        modifier = modifier,
        bottomBar = {
            NavigationBar {
                TABS.forEach { tab ->
                    val selected = currentRoute == tab.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (selected && tab.route == TabRoute.HOME) {
                                homeReselections.tryEmit(Unit)
                            } else if (!selected) {
                                tabNavController.navigate(tab.route) {
                                    popUpTo(tabNavController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = {
                            Icon(
                                if (selected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.label,
                            )
                        },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = tabNavController,
            startDestination = TabRoute.HOME,
            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
        ) {
            composable(TabRoute.HOME) {
                HomeScreen(onVideoClick = onVideoClick, onSearchClick = onSearchClick,
                    onLoginClick = onLoginClick, onSettingsClick = onSettingsClick,
                    homeReselections = homeReselections)
            }
            composable(TabRoute.RANKING) {
                RankingScreen(onVideoClick = onVideoClick)
            }
            composable(TabRoute.DYNAMIC) {
                DynamicScreen(
                    onLoginClick = onLoginClick,
                    onAuthorClick = onSpaceClick,
                    onVideoClick = onVideoClick,
                    onOpusClick = onOpusClick,
                    onArticleClick = onArticleClick,
                )
            }
            composable(TabRoute.PROFILE) {
                ProfileScreen(
                    onLoginClick = onLoginClick,
                    onSpaceClick = onSpaceClick,
                    onFavoritesClick = onFavoritesClick,
                    onWatchLaterClick = onWatchLaterClick,
                    onMessagesClick = onMessagesClick,
                    onSettingsClick = onSettingsClick,
                )
            }
        }
    }
}
