package com.sabil.qnow.ui.root

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.messaging.FirebaseMessaging
import com.sabil.qnow.data.local.SecureStore
import com.sabil.qnow.domain.model.UserRole
import com.sabil.qnow.domain.repository.AuthRepository
import com.sabil.qnow.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

sealed interface RootState {
    data object Loading : RootState
    data object SignedOut : RootState
    data class NeedsRole(val suggestedName: String?) : RootState
    data object Customer : RootState
    data object Business : RootState
}

@HiltViewModel
class RootViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val users: UserRepository,
    private val secureStore: SecureStore,
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<RootState> = auth.authState
        .distinctUntilChanged()
        .flatMapLatest { uid ->
            if (uid == null) return@flatMapLatest flowOf<RootState>(RootState.SignedOut)
            registerPush()
            flow<RootState> {
                // Show the cached (encrypted) role immediately, then follow the server copy.
                secureStore.role(uid)?.let { emit(it.toState()) }
                users.observeUser(uid)
                    .map { user -> user?.role?.toState() ?: RootState.NeedsRole(user?.displayName) }
                    .catch { emit(RootState.NeedsRole(null)) }
                    .collect { emit(it) }
            }
        }
        .onStart<RootState> { emit(RootState.Loading) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, RootState.Loading)

    private fun UserRole.toState() = if (this == UserRole.BUSINESS) RootState.Business else RootState.Customer

    private fun registerPush() {
        viewModelScope.launch {
            runCatching {
                val token = FirebaseMessaging.getInstance().token.await()
                users.registerPushToken(token)
            }
        }
    }

    fun signOut() {
        viewModelScope.launch { auth.signOut() }
    }
}
