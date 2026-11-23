package com.sabil.qnow.data.messaging

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.sabil.qnow.domain.repository.UserRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Receives data-only pushes from the `queueAlerts` Cloud Function. The payload carries only
 * ids, the token number and the business name; never any customer details.
 */
@AndroidEntryPoint
class QNowMessagingService : FirebaseMessagingService() {

    @Inject lateinit var notifier: QueueNotifier
    @Inject lateinit var users: UserRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        scope.launch { runCatching { users.registerPushToken(token) } }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val alert = QueueAlert.from(data["alert"]) ?: return
        val businessId = data["businessId"] ?: return
        val tokenId = data["tokenId"] ?: return
        val number = data["number"]?.toIntOrNull() ?: return
        notifier.show(alert, businessId, tokenId, number, data["businessName"].orEmpty())
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
