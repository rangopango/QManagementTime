package com.sabil.qnow.data.messaging

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.sabil.qnow.domain.model.TokenStatus
import com.sabil.qnow.domain.queue.ObserveLiveBusiness
import com.sabil.qnow.domain.repository.QueueRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Follows one live token on the customer's own phone and raises the 15-minute, 5-minute and
 * your-turn alerts. This replaces server push (which needs a paid plan): Firestore listeners
 * keep it up to date, and a silent ongoing notification shows the live position. It stops by
 * itself once the token is done, cancelled or skipped.
 */
@AndroidEntryPoint
class TokenTrackerService : Service() {

    @Inject lateinit var queue: QueueRepository
    @Inject lateinit var observeLive: ObserveLiveBusiness
    @Inject lateinit var notifier: QueueNotifier

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private var tracking: Pair<String, String>? = null
    private val alerted = mutableSetOf<QueueAlert>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val businessId = intent?.getStringExtra(EXTRA_BUSINESS) ?: tracking?.first
        val tokenId = intent?.getStringExtra(EXTRA_TOKEN) ?: tracking?.second
        if (businessId == null || tokenId == null) {
            stopSelf(); return START_NOT_STICKY
        }
        ServiceCompat.startForeground(
            this,
            QueueNotifier.ONGOING_ID,
            notifier.ongoing(businessId, tokenId, "Your token", "Connecting to the live queue…"),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
        if (tracking != businessId to tokenId) {
            tracking = businessId to tokenId
            alerted.clear()
            follow(businessId, tokenId)
        }
        return START_REDELIVER_INTENT
    }

    private fun follow(businessId: String, tokenId: String) {
        job?.cancel()
        job = scope.launch {
            combine(queue.observeToken(businessId, tokenId), observeLive(businessId)) { token, live -> token to live }
                .catch { stopTracking() }
                .collect { (token, live) ->
                    if (token == null || live == null) return@collect
                    val name = live.business.name
                    if (!token.status.isActive) {
                        stopTracking(); return@collect
                    }
                    val estimate = live.summary.estimates[token.id]
                    val due = when {
                        token.status == TokenStatus.SERVING || token.status == TokenStatus.GRACE -> QueueAlert.YOUR_TURN
                        (estimate?.waitMinutes ?: Int.MAX_VALUE) <= 5 -> QueueAlert.FIVE_MINUTES
                        (estimate?.waitMinutes ?: Int.MAX_VALUE) <= 15 -> QueueAlert.FIFTEEN_MINUTES
                        else -> null
                    }
                    if (due != null && due !in alerted) {
                        // Marking the earlier alerts too means a jump straight to 5 min doesn't also send "15 min".
                        alerted += QueueAlert.entries.filter { it.ordinal <= due.ordinal }
                        notifier.show(due, businessId, tokenId, token.number, name)
                    }
                    val text = when (token.status) {
                        TokenStatus.WAITING -> {
                            val wait = estimate?.waitMinutes ?: 0
                            val ahead = estimate?.peopleAhead ?: 0
                            "About ${if (wait <= 0) "a moment" else "$wait min"} · $ahead ahead of you"
                        }
                        TokenStatus.GRACE -> "You've been called. Please go in now"
                        else -> "It's your turn"
                    }
                    notifier.updateOngoing(businessId, tokenId, "Token #${token.number} at $name", text)
                }
        }
    }

    private fun stopTracking() {
        tracking = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_BUSINESS = "businessId"
        private const val EXTRA_TOKEN = "tokenId"

        fun start(context: Context, businessId: String, tokenId: String) {
            val intent = Intent(context, TokenTrackerService::class.java)
                .putExtra(EXTRA_BUSINESS, businessId)
                .putExtra(EXTRA_TOKEN, tokenId)
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }
    }
}

/** Starts live tracking for a token. Called whenever the app sees the customer has a live token. */
@Singleton
class TokenTracker @Inject constructor(
    @param:dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
) {
    private var last: Pair<String, String>? = null

    fun track(businessId: String, tokenId: String) {
        if (last == businessId to tokenId) return
        last = businessId to tokenId
        TokenTrackerService.start(context, businessId, tokenId)
    }

    fun forget() { last = null }
}
