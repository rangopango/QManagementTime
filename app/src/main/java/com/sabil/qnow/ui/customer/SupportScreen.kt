package com.sabil.qnow.ui.customer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sabil.qnow.domain.repository.SupportRepository
import com.sabil.qnow.domain.repository.SupportTicket
import com.sabil.qnow.ui.components.Pill
import com.sabil.qnow.ui.components.PrimaryButton
import com.sabil.qnow.ui.components.QCard
import com.sabil.qnow.ui.theme.QNowTheme
import com.sabil.qnow.ui.theme.Space
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private val categories = listOf(
    "Booking problem", "Wait time / queue", "Complaint about a shop", "App not working",
    "Account & privacy", "Suggestion", "Other",
)

data class SupportForm(val category: String? = null, val message: String = "", val sending: Boolean = false, val sent: Boolean = false, val error: String? = null)

@HiltViewModel
class SupportViewModel @Inject constructor(private val support: SupportRepository) : ViewModel() {
    val form = MutableStateFlow(SupportForm())
    val mine = support.observeMine().catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun pick(c: String) = form.update { it.copy(category = c, sent = false, error = null) }
    fun write(t: String) = form.update { it.copy(message = t.take(2000), sent = false, error = null) }

    fun send() {
        val f = form.value
        if (f.category == null || f.message.isBlank()) return
        form.update { it.copy(sending = true, error = null) }
        viewModelScope.launch {
            runCatching { support.submit(f.category, f.message) }
                .onSuccess { form.value = SupportForm(sent = true) }
                .onFailure { form.update { it.copy(sending = false, error = "Couldn't send. Check your connection and try again.") } }
        }
    }
}

/** Customer helpline: pick a topic, describe the issue, and track the reply. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SupportScreen(onBack: () -> Unit, vm: SupportViewModel = hiltViewModel()) {
    val form by vm.form.collectAsStateWithLifecycle()
    val mine by vm.mine.collectAsStateWithLifecycle()

    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding(),
        contentPadding = PaddingValues(Space.screen),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        item {
            Row(Modifier.statusBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                Text("Customer helpline", style = MaterialTheme.typography.headlineSmall)
            }
            Text(
                "Tell us what's wrong or ask anything. Our team reads every message and replies here.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            QCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.md)) {
                    Text("What is it about?", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                        categories.forEach { c -> FilterChip(form.category == c, onClick = { vm.pick(c) }, label = { Text(c) }, shape = CircleShape) }
                    }
                    OutlinedTextField(
                        value = form.message, onValueChange = vm::write, minLines = 4,
                        label = { Text("Describe the problem or question") },
                        supportingText = { Text("Don't include passwords or card details. ${form.message.length}/2000") },
                        shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth(),
                    )
                    form.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (form.sent) Text("Sent! We'll reply here as soon as we can.", color = QNowTheme.extended.open)
                    PrimaryButton(
                        "Send to QNow team", onClick = vm::send, icon = Icons.AutoMirrored.Rounded.Send,
                        enabled = form.category != null && form.message.isNotBlank(), loading = form.sending,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        if (mine.isNotEmpty()) item { Text("Your requests", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = Space.sm)) }
        items(mine, key = { it.id }) { TicketRow(it) }
    }
}

@Composable
private fun TicketRow(t: SupportTicket) {
    val (label, color) = when (t.status) {
        "RESOLVED" -> "Resolved" to QNowTheme.extended.open
        "REPLIED" -> "Replied" to MaterialTheme.colorScheme.primary
        else -> "Open" to QNowTheme.extended.onBreak
    }
    QCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Space.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t.category, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Pill(label, color)
            }
            Text(t.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3)
            t.reply?.let {
                Text("QNow: $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Space.sm))
            }
        }
    }
}
