package com.sabil.qnow.data.messaging

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.sabil.qnow.MainActivity
import com.sabil.qnow.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** The three alerts a customer gets for a token. */
enum class QueueAlert(val key: String) {
    FIFTEEN_MINUTES("15"),
    FIVE_MINUTES("5"),
    YOUR_TURN("turn");

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key }
    }
}

/**
 * Shows queue alerts on the phone itself. Each alert has a fixed tag and id per token, so the
 * same alert never stacks up twice.
 */
@Singleton
class QueueNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_queue_alerts),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = context.getString(R.string.channel_queue_alerts_desc) }
        val live = NotificationChannel(LIVE_CHANNEL_ID, "Live token", NotificationManager.IMPORTANCE_LOW)
            .apply { description = "Shows your place in line while you hold a token"; setShowBadge(false) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannels(listOf(channel, live))
    }

    private fun openToken(businessId: String, tokenId: String) = PendingIntent.getActivity(
        context,
        tokenId.hashCode(),
        Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_BUSINESS_ID, businessId)
            .putExtra(MainActivity.EXTRA_TOKEN_ID, tokenId)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Silent, ongoing notification with the live position. Required for the tracking service. */
    fun ongoing(businessId: String, tokenId: String, title: String, text: String) =
        NotificationCompat.Builder(context, LIVE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.brand_primary))
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(openToken(businessId, tokenId))
            .build()

    fun updateOngoing(businessId: String, tokenId: String, title: String, text: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(ONGOING_ID, ongoing(businessId, tokenId, title, text))
    }

    fun show(alert: QueueAlert, businessId: String, tokenId: String, number: Int, businessName: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        val (title, body) = when (alert) {
            QueueAlert.FIFTEEN_MINUTES -> "About 15 minutes to go" to "Token #$number at $businessName. Time to head over."
            QueueAlert.FIVE_MINUTES -> "About 5 minutes left" to "Token #$number at $businessName. You're almost up."
            QueueAlert.YOUR_TURN -> "It's your turn!" to "Token #$number — please go to the counter at $businessName."
        }
        val open = PendingIntent.getActivity(
            context,
            tokenId.hashCode(),
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_BUSINESS_ID, businessId)
                .putExtra(MainActivity.EXTRA_TOKEN_ID, tokenId)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.brand_primary))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify("$businessId/$tokenId", alert.ordinal, notification)
    }

    companion object {
        const val CHANNEL_ID = "queue_alerts"
        const val LIVE_CHANNEL_ID = "live_token"
        const val ONGOING_ID = 4207
    }
}
