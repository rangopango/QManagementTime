package com.sabil.qnow

import android.app.Application
import com.google.firebase.FirebaseApp
import com.sabil.qnow.core.CrashReport
import com.sabil.qnow.data.messaging.QueueNotifier
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class QNowApp : Application() {

    @Inject lateinit var notifier: QueueNotifier

    override fun onCreate() {
        super.onCreate()
        CrashReport.install(this)
        notifier.createChannel()
        // App Check protects the AI (Gemini) calls; only when Firebase is configured.
        if (isFirebaseConfigured) runCatching { com.sabil.qnow.core.AppCheckSetup.install() }
        // OpenStreetMap tiles: identify the app (required by the tile usage policy) and cache inside app storage.
        org.osmdroid.config.Configuration.getInstance().apply {
            userAgentValue = BuildConfig.APPLICATION_ID
            osmdroidBasePath = java.io.File(cacheDir, "osmdroid")
            osmdroidTileCache = java.io.File(cacheDir, "osmdroid/tiles")
        }
    }

    /** False when the build has no google-services.json (Firebase never initialised). */
    val isFirebaseConfigured: Boolean
        get() = FirebaseApp.getApps(this).isNotEmpty()
}
