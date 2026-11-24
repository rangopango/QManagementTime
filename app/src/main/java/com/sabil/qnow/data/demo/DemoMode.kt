package com.sabil.qnow.data.demo

import android.content.Context
import android.content.Intent
import com.sabil.qnow.MainActivity
import com.sabil.qnow.domain.model.UserRole
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.system.exitProcess

/**
 * Prototype mode: the whole app runs on an in-memory world of bot shops and bot customers
 * instead of Firebase, so it can be explored without accounts. Read once at start-up (the
 * repositories are chosen then), so switching restarts the app.
 */
@Singleton
class DemoMode @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val prefs = context.getSharedPreferences("qnow_demo", Context.MODE_PRIVATE)

    val enabled: Boolean = prefs.getBoolean(KEY_ON, false)
    val role: UserRole = if (prefs.getString(KEY_ROLE, null) == UserRole.BUSINESS.name) UserRole.BUSINESS else UserRole.CUSTOMER

    fun enter(role: UserRole) {
        prefs.edit().putBoolean(KEY_ON, true).putString(KEY_ROLE, role.name).commit()
        restart()
    }

    fun exit() {
        prefs.edit().clear().commit()
        restart()
    }

    private fun restart() {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
        exitProcess(0)
    }

    private companion object {
        const val KEY_ON = "on"
        const val KEY_ROLE = "role"
    }
}
