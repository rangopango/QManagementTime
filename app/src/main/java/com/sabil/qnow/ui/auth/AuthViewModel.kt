package com.sabil.qnow.ui.auth

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.sabil.qnow.domain.model.UserRole
import com.sabil.qnow.domain.repository.AuthRepository
import com.sabil.qnow.domain.repository.PhoneAuthStep
import com.sabil.qnow.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class AuthUiState(
    val countryCode: String = "+91",
    val phone: String = "",
    val code: String = "",
    val verificationId: String? = null,
    val loading: Boolean = false,
    val googleLoading: Boolean = false,
    val error: String? = null,
) {
    val fullNumber: String get() = countryCode + phone.filter(Char::isDigit)
    val canSend: Boolean get() = phone.count(Char::isDigit) in 6..14 && !loading
    val canVerify: Boolean get() = code.length == 6 && !loading
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val demo: com.sabil.qnow.data.demo.DemoMode,
) : ViewModel() {

    /** Opens the prototype world with bot shops and customers (restarts the app). */
    fun tryDemo(role: UserRole) = demo.enter(role)

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun onCountryCode(v: String) = _state.update { it.copy(countryCode = "+" + v.filter(Char::isDigit).take(4), error = null) }
    fun onPhone(v: String) = _state.update { it.copy(phone = v.filter { c -> c.isDigit() || c == ' ' }.take(16), error = null) }
    fun onCode(v: String) = _state.update { it.copy(code = v.filter(Char::isDigit).take(6), error = null) }
    fun editNumber() = _state.update { it.copy(verificationId = null, code = "", error = null) }

    fun sendCode(activity: Activity) {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            auth.sendPhoneCode(activity, _state.value.fullNumber)
                .catch { e -> _state.update { it.copy(loading = false, error = friendly(e)) } }
                .collect { step ->
                    when (step) {
                        is PhoneAuthStep.CodeSent -> _state.update { it.copy(loading = false, verificationId = step.verificationId) }
                        PhoneAuthStep.AutoVerified -> _state.update { it.copy(loading = false) }
                    }
                }
        }
    }

    fun verify() {
        val id = _state.value.verificationId ?: return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { auth.verifyPhoneCode(id, _state.value.code) }
                .onFailure { e -> _state.update { it.copy(loading = false, error = friendly(e)) } }
                .onSuccess { _state.update { it.copy(loading = false) } }
        }
    }

    fun google(activity: Activity) {
        _state.update { it.copy(googleLoading = true, error = null) }
        viewModelScope.launch {
            runCatching { auth.signInWithGoogle(activity) }
                .onFailure { e ->
                    _state.update {
                        it.copy(googleLoading = false, error = if (e is GetCredentialCancellationException) null else friendly(e))
                    }
                }
                .onSuccess { _state.update { it.copy(googleLoading = false) } }
        }
    }

    // Messages never echo back the phone number or raw server text.
    private fun friendly(e: Throwable): String {
        val text = when (e) {
            is FirebaseAuthInvalidCredentialsException -> "That code or number doesn't look right. Please check and try again."
            is FirebaseTooManyRequestsException -> "Too many attempts. Please wait a little and try again."
            is FirebaseNetworkException -> "No internet connection. Check your network and try again."
            is com.sabil.qnow.data.auth.GoogleSignInUnavailableException -> e.message!!
            is androidx.credentials.exceptions.NoCredentialException -> "No Google account found on this device. Add one in Settings, or use your phone number."
            else -> "Sign-in didn't work. Please try again."
        }
        return "$text\n(Error: ${errorCode(e)})"
    }

    /**
     * A short code for support, e.g. "ERROR_APP_NOT_AUTHORIZED" or "GetCredentialUnknownException".
     * Only the error type and code are shown: never the phone number or token.
     */
    private fun errorCode(e: Throwable): String {
        val firebaseCode = (e as? com.google.firebase.auth.FirebaseAuthException)?.errorCode
        val credentialType = (e as? androidx.credentials.exceptions.GetCredentialException)?.type?.substringAfterLast('.')
        val gmsStatus = Regex("""\b(\d{2,5}):""").find(e.message.orEmpty())?.groupValues?.get(1)
        return listOfNotNull(firebaseCode ?: credentialType ?: e.javaClass.simpleName, gmsStatus).joinToString(" ")
    }
}

data class RoleUiState(val name: String = "", val saving: Boolean = false, val error: String? = null)

@HiltViewModel
class RoleViewModel @Inject constructor(
    private val users: UserRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(RoleUiState())
    val state: StateFlow<RoleUiState> = _state.asStateFlow()

    fun prefill(name: String?) {
        if (_state.value.name.isEmpty() && !name.isNullOrBlank()) _state.update { it.copy(name = name) }
    }

    fun onName(v: String) = _state.update { it.copy(name = v.take(40)) }

    fun choose(role: UserRole) {
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            runCatching {
                users.chooseRole(role, _state.value.name.ifBlank { null })
            }.onFailure { _state.update { it.copy(saving = false, error = "Couldn't save that. Check your connection and try again.") } }
        }
    }
}
