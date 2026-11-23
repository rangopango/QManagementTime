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
    }

    /** False when the build has no google-services.json (Firebase never initialised). */
    val isFirebaseConfigured: Boolean
        get() = FirebaseApp.getApps(this).isNotEmpty()
}
