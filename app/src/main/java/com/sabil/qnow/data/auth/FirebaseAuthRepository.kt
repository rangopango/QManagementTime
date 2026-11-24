package com.sabil.qnow.data.auth

import android.annotation.SuppressLint
import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.sabil.qnow.data.local.SecureStore
import com.sabil.qnow.domain.repository.AccountInfo
import com.sabil.qnow.domain.repository.AuthRepository
import com.sabil.qnow.domain.repository.PhoneAuthStep
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

class GoogleSignInUnavailableException :
    IllegalStateException("Google sign-in isn't set up for this build yet. Use your phone number.")

@Singleton
class FirebaseAuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val secureStore: SecureStore,
) : AuthRepository {

    override val currentUserId: String? get() = auth.currentUser?.uid

    override val authState: Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.uid) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    override fun sendPhoneCode(activity: Activity, phoneNumber: String): Flow<PhoneAuthStep> = callbackFlow {
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                launch {
                    runCatching { auth.signInWithCredential(credential).await() }
                        .onSuccess { trySend(PhoneAuthStep.AutoVerified); close() }
                        .onFailure { close(it) }
                }
            }

            override fun onVerificationFailed(e: FirebaseException) {
                close(e)
            }

            override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
                trySend(PhoneAuthStep.CodeSent(verificationId))
            }
        }
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
        awaitClose { }
    }

    override suspend fun verifyPhoneCode(verificationId: String, code: String) {
        val credential = PhoneAuthProvider.getCredential(verificationId, code)
        auth.signInWithCredential(credential).await()
    }

    override suspend fun signInWithGoogle(activity: Activity) {
        val clientId = webClientId(activity) ?: throw GoogleSignInUnavailableException()
        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(false)
            .build()
        val result = CredentialManager.create(activity)
            .getCredential(activity, GetCredentialRequest.Builder().addCredentialOption(option).build())
        val credential = result.credential
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) error("Unexpected sign-in response")
        val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
    }

    override fun account(): AccountInfo? {
        val user = auth.currentUser ?: return null
        val providers = user.providerData.map { it.providerId }
        return AccountInfo(
            name = user.displayName?.takeIf { it.isNotBlank() },
            email = user.email?.takeIf { it.isNotBlank() },
            phone = user.phoneNumber?.takeIf { it.isNotBlank() },
            photoUrl = user.photoUrl?.toString(),
            signedInWith = when {
                "google.com" in providers -> "Google"
                "phone" in providers -> "Phone number"
                else -> "QNow account"
            },
        )
    }

    override suspend fun signOut() {
        secureStore.clear()
        auth.signOut()
    }

    /**
     * The OAuth client id comes from google-services.json at build time. It's looked up by
     * name so the app still compiles before Firebase is set up.
     */
    @SuppressLint("DiscouragedApi")
    private fun webClientId(activity: Activity): String? {
        val id = activity.resources.getIdentifier("default_web_client_id", "string", activity.packageName)
        return if (id == 0) null else activity.getString(id)
    }
}
