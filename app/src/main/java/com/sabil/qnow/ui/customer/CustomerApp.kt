package com.sabil.qnow.ui.customer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ConfirmationNumber
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import com.sabil.qnow.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sabil.qnow.ui.navigation.BusinessRoute
import com.sabil.qnow.ui.navigation.HomeRoute
import com.sabil.qnow.ui.navigation.MapRoute
import com.sabil.qnow.ui.navigation.MyTokensRoute
import com.sabil.qnow.ui.navigation.ReviewRoute
import com.sabil.qnow.ui.navigation.TokenRoute
import com.sabil.qnow.ui.navigation.TryOnRoute
import com.sabil.qnow.ui.navigation.ProfileRoute
import com.sabil.qnow.ui.navigation.FavouritesRoute
import com.sabil.qnow.ui.navigation.HelpRoute
import com.sabil.qnow.ui.navigation.AiStyleRoute
import com.sabil.qnow.ui.navigation.LiveMirrorRoute
import com.sabil.qnow.ui.navigation.FamilyRoute
import com.sabil.qnow.ui.navigation.SupportRoute
import com.sabil.qnow.ui.navigation.AdsRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.reflect.KClass

private data class Tab(val route: Any, val type: KClass<*>, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(HomeRoute, HomeRoute::class, "Home", Icons.Rounded.Home),
    Tab(MapRoute, MapRoute::class, "Explore", Icons.Rounded.Map),
    Tab(AiStyleRoute, AiStyleRoute::class, "Looks", Icons.Rounded.AutoAwesome),
    Tab(MyTokensRoute, MyTokensRoute::class, "My tokens", Icons.Rounded.ConfirmationNumber),
)

@Composable
fun CustomerApp(pendingToken: MutableStateFlow<TokenRoute?>, onSignOut: () -> Unit) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val destination = entry?.destination
    val showBar = tabs.any { t -> destination?.hasRoute(t.type) == true }

    val deepLink by pendingToken.collectAsStateWithLifecycle()
    LaunchedEffect(deepLink) {
        deepLink?.let {
            nav.navigate(it) { launchSingleTop = true }
            pendingToken.value = null
        }
    }

    Scaffold(
        bottomBar = {
            AnimatedVisibility(showBar, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = destination?.hasRoute(tab.type) == true,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(bottom = if (showBar) padding.calculateBottomPadding() else androidx.compose.ui.unit.Dp.Hairline)) {
            NavHost(
                navController = nav,
                startDestination = HomeRoute,
                enterTransition = { slideInHorizontally { it / 6 } + fadeIn() },
                exitTransition = { fadeOut() },
                popEnterTransition = { fadeIn() },
                popExitTransition = { slideOutHorizontally { it / 6 } + fadeOut() },
            ) {
                composable<HomeRoute> {
                    HomeScreen(
                        onFind = { nav.navigate(MapRoute) { launchSingleTop = true } },
                        onBusiness = { nav.navigate(BusinessRoute(it)) },
                        onToken = { b, t -> nav.navigate(TokenRoute(b, t)) },
                        onTryOn = { nav.navigate(AiStyleRoute) { launchSingleTop = true } },
                        onProfile = { nav.navigate(ProfileRoute) },
                    )
                }
                composable<MapRoute> { MapScreen(onBusiness = { nav.navigate(BusinessRoute(it)) }) }
                composable<MyTokensRoute> {
                    MyTokensScreen(onToken = { b, t -> nav.navigate(TokenRoute(b, t)) }, onFind = { nav.navigate(MapRoute) })
                }
                composable<BusinessRoute> {
                    BusinessDetailScreen(
                        onBack = nav::popBackStack,
                        onBooked = { route ->
                            nav.navigate(route) { popUpTo<BusinessRoute> { inclusive = true } }
                        },
                        onToken = { b, t -> nav.navigate(TokenRoute(b, t)) },
                    )
                }
                composable<TokenRoute> {
                    TokenScreen(
                        onBack = { if (!nav.popBackStack()) nav.navigate(HomeRoute) },
                        onReview = { nav.navigate(it) },
                        onBusiness = { nav.navigate(BusinessRoute(it)) },
                    )
                }
                composable<ReviewRoute> { ReviewScreen(onDone = nav::popBackStack) }
                composable<TryOnRoute> { TryOnScreen(onBack = nav::popBackStack) }
                composable<ProfileRoute> {
                    ProfileScreen(
                        onBack = nav::popBackStack,
                        onMyTokens = { nav.navigate(MyTokensRoute) { launchSingleTop = true } },
                        onFavourites = { nav.navigate(FavouritesRoute) },
                        onHelp = { nav.navigate(HelpRoute) },
                        onTryOn = { nav.navigate(AiStyleRoute) { launchSingleTop = true } },
                        onFamily = { nav.navigate(FamilyRoute) },
                        onSupport = { nav.navigate(SupportRoute) },
                        onAds = { nav.navigate(AdsRoute) },
                    )
                }
                composable<FavouritesRoute> {
                    FavouritesScreen(
                        onBack = nav::popBackStack,
                        onBusiness = { nav.navigate(BusinessRoute(it)) },
                        onFind = { nav.navigate(MapRoute) { launchSingleTop = true } },
                    )
                }
                composable<HelpRoute> { HelpScreen(onBack = nav::popBackStack, onSupport = { nav.navigate(SupportRoute) }) }
                composable<FamilyRoute> { FamilyScreen(onBack = nav::popBackStack) }
                composable<SupportRoute> { SupportScreen(onBack = nav::popBackStack) }
                composable<AdsRoute> { AdsScreen(onBack = nav::popBackStack) }
                composable<AiStyleRoute> { AiStyleScreen(onFindBarber = { nav.navigate(MapRoute) { launchSingleTop = true } }, onLiveMirror = { nav.navigate(LiveMirrorRoute) }) }
                composable<LiveMirrorRoute> { LiveMirrorScreen(onClose = nav::popBackStack) }
            }
        }
    }
}
