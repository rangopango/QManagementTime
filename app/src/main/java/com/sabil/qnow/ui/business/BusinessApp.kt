package com.sabil.qnow.ui.business

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sabil.qnow.ui.auth.SplashScreen
import com.sabil.qnow.ui.navigation.DashboardRoute
import com.sabil.qnow.ui.navigation.EditBusinessRoute

@Composable
fun BusinessApp(onSignOut: () -> Unit, vm: MyBusinessViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    when (state) {
        MyBusinessState.Loading -> SplashScreen()
        // Registration finishes by writing the business doc, which flips this to Registered.
        MyBusinessState.NotRegistered -> BusinessFormScreen(onClose = null, onSaved = {})
        is MyBusinessState.Registered -> {
            val nav = rememberNavController()
            NavHost(nav, startDestination = DashboardRoute) {
                composable<DashboardRoute> {
                    DashboardScreen(onEdit = { nav.navigate(EditBusinessRoute) }, onSignOut = onSignOut)
                }
                composable<EditBusinessRoute>(
                    enterTransition = { slideInVertically { it } + fadeIn() },
                    popExitTransition = { slideOutVertically { it } + fadeOut() },
                ) {
                    BusinessFormScreen(onClose = nav::popBackStack, onSaved = { nav.popBackStack() })
                }
            }
        }
    }
}
