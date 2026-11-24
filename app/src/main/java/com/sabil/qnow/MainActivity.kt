package com.sabil.qnow

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sabil.qnow.core.CrashReport
import com.sabil.qnow.ui.auth.AuthScreen
import com.sabil.qnow.ui.auth.FirebaseNotConfiguredScreen
import com.sabil.qnow.ui.auth.RoleScreen
import com.sabil.qnow.ui.auth.SplashScreen
import com.sabil.qnow.ui.business.BusinessApp
import com.sabil.qnow.ui.customer.CustomerApp
import com.sabil.qnow.ui.navigation.TokenRoute
import com.sabil.qnow.ui.root.RootState
import com.sabil.qnow.ui.root.RootViewModel
import com.sabil.qnow.ui.theme.QNowTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import androidx.compose.foundation.isSystemInDarkTheme
import com.sabil.qnow.data.local.AppSettings
import com.sabil.qnow.data.local.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settings: AppSettings
    @Inject lateinit var demoMode: com.sabil.qnow.data.demo.DemoMode

    /** A token opened from a notification, consumed by the customer navigation. */
    private val pendingToken = MutableStateFlow<TokenRoute?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        CrashReport.read(this)?.let { report ->
            CrashReport.show(this, report)
            return
        }
        enableEdgeToEdge()
        readDeepLink(intent)

        // Demo mode runs entirely offline, so it works even before Firebase is set up.
        val configured = (application as QNowApp).isFirebaseConfigured || demoMode.enabled
        setContent {
            val theme by settings.theme.collectAsStateWithLifecycle(ThemeMode.SYSTEM)
            val dark = when (theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            QNowTheme(darkTheme = dark) {
                if (!configured) {
                    FirebaseNotConfiguredScreen(onDemo = demoMode::enter)
                } else {
                    val root: RootViewModel = hiltViewModel()
                    val state by root.state.collectAsStateWithLifecycle()
                    AnimatedContent(
                        targetState = state,
                        contentKey = { it::class },
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "root",
                    ) { s ->
                        when (s) {
                            RootState.Loading -> SplashScreen()
                            RootState.SignedOut -> AuthScreen()
                            is RootState.NeedsRole -> RoleScreen(s.suggestedName, onSignOut = root::signOut)
                            RootState.Customer -> CustomerApp(pendingToken = pendingToken, onSignOut = root::signOut)
                            RootState.Business -> BusinessApp(onSignOut = root::signOut)
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readDeepLink(intent)
    }

    private fun readDeepLink(intent: Intent?) {
        val biz = intent?.getStringExtra(EXTRA_BUSINESS_ID) ?: return
        val token = intent.getStringExtra(EXTRA_TOKEN_ID) ?: return
        pendingToken.value = TokenRoute(biz, token)
        intent.removeExtra(EXTRA_BUSINESS_ID)
    }

    companion object {
        const val EXTRA_BUSINESS_ID = "qnow.businessId"
        const val EXTRA_TOKEN_ID = "qnow.tokenId"
    }
}
