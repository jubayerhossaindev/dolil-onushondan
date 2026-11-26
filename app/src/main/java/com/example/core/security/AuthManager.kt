package com.example.core.security

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import com.example.domain.model.AppLanguage
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

private const val AUTH_TAG = "Auth"

enum class AuthValidationError {
    INVALID_FULL_NAME,
    INVALID_EMAIL,
    PASSWORD_TOO_SHORT,
    PASSWORD_NEEDS_LETTER_AND_DIGIT,
    PASSWORDS_DO_NOT_MATCH
}

object AuthValidator {
    private val emailRegex = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    fun validateEmail(email: String): AuthValidationError? {
        val trimmed = email.trim()
        return if (trimmed.isBlank() || !emailRegex.matches(trimmed)) {
            AuthValidationError.INVALID_EMAIL
        } else {
            null
        }
    }

    fun validateRegistration(
        fullName: String,
        email: String,
        password: String,
        confirmPassword: String
    ): List<AuthValidationError> {
        val errors = mutableListOf<AuthValidationError>()
        if (fullName.trim().length < 2) {
            errors.add(AuthValidationError.INVALID_FULL_NAME)
        }
        validateEmail(email)?.let(errors::add)
        if (password.length < 8) {
            errors.add(AuthValidationError.PASSWORD_TOO_SHORT)
        } else if (!password.any { it.isLetter() } || !password.any { it.isDigit() }) {
            errors.add(AuthValidationError.PASSWORD_NEEDS_LETTER_AND_DIGIT)
        }
        if (password != confirmPassword) {
            errors.add(AuthValidationError.PASSWORDS_DO_NOT_MATCH)
        }
        return errors
    }

    fun validateSignIn(email: String, password: String): List<AuthValidationError> {
        val errors = mutableListOf<AuthValidationError>()
        validateEmail(email)?.let(errors::add)
        if (password.isBlank()) {
            errors.add(AuthValidationError.PASSWORD_TOO_SHORT)
        }
        return errors
    }

    fun validationMessage(error: AuthValidationError, language: AppLanguage): String = when (error) {
        AuthValidationError.INVALID_FULL_NAME ->
            if (language == AppLanguage.BN) "কমপক্ষে ২ অক্ষরের পূর্ণ নাম লিখুন।" else "Please enter your full name (at least 2 characters)."
        AuthValidationError.INVALID_EMAIL ->
            if (language == AppLanguage.BN) "সঠিক ইমেইল ঠিকানা লিখুন।" else "Please enter a valid email address."
        AuthValidationError.PASSWORD_TOO_SHORT ->
            if (language == AppLanguage.BN) "পাসওয়ার্ড কমপক্ষে ৮ অক্ষরের হতে হবে।" else "Password must be at least 8 characters long."
        AuthValidationError.PASSWORD_NEEDS_LETTER_AND_DIGIT ->
            if (language == AppLanguage.BN) "পাসওয়ার্ডে অন্তত একটি অক্ষর এবং একটি সংখ্যা থাকতে হবে।" else "Password must include at least one letter and one number."
        AuthValidationError.PASSWORDS_DO_NOT_MATCH ->
            if (language == AppLanguage.BN) "পাসওয়ার্ড ও নিশ্চিত পাসওয়ার্ড মিলছে না।" else "Passwords do not match."
    }

    fun mapFirebaseException(error: Throwable, language: AppLanguage): String = when (error) {
        is FirebaseAuthWeakPasswordException ->
            if (language == AppLanguage.BN) "পাসওয়ার্ডটি অত্যন্ত দুর্বল। অক্ষর ও সংখ্যা মিশিয়ে শক্তিশালী পাসওয়ার্ড দিন।" else "Password is too weak. Use at least 8 characters with letters and numbers."
        is FirebaseAuthUserCollisionException ->
            if (language == AppLanguage.BN) "এই ইমেইল দিয়ে ইতিমধ্যেই একটি অ্যাকাউন্ট রয়েছে।" else "An account with this email already exists."
        is FirebaseAuthInvalidUserException ->
            if (language == AppLanguage.BN) "এই ইমেইলে কোনো অ্যাকাউন্ট পাওয়া যায়নি।" else "No account found with this email address."
        is FirebaseAuthInvalidCredentialsException ->
            if (language == AppLanguage.BN) "ইমেইল বা পাসওয়ার্ড সঠিক নয়।" else "Incorrect email or password."
        is FirebaseAuthRecentLoginRequiredException ->
            if (language == AppLanguage.BN) "নিরাপত্তার জন্য দয়া করে পুনরায় যাচাই (Re-authenticate) করুন।" else "Please re-authenticate to complete this sensitive action."
        is FirebaseTooManyRequestsException ->
            if (language == AppLanguage.BN) "অনেকবার চেষ্টা করা হয়েছে। কিছুক্ষণ পর আবার চেষ্টা করুন।" else "Too many attempts. Please wait a moment and try again."
        is FirebaseNetworkException ->
            if (language == AppLanguage.BN) "ইন্টারনেট সংযোগ নেই বা সার্ভার পাওয়া যাচ্ছে না।" else "Network error. Please check your internet connection."
        else -> error.localizedMessage ?: if (language == AppLanguage.BN) {
            "অথেনটিকেশন সম্পন্ন করা যায়নি। আবার চেষ্টা করুন।"
        } else {
            "Authentication failed. Please try again."
        }
    }
}

sealed interface AuthActionOutcome {
    data class Success(
        val uid: String,
        val email: String,
        val displayName: String,
        val isEmailVerified: Boolean,
        val verificationEmailSent: Boolean = false
    ) : AuthActionOutcome

    data class Cancelled(val reason: String = "Cancelled") : AuthActionOutcome
    data class Error(val message: String, val requiresReauth: Boolean = false) : AuthActionOutcome
}

fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

object AuthManager {

    fun isFirebaseConfigured(context: Context): Boolean = try {
        val appContext = context.applicationContext
        FirebaseApp.getApps(appContext).isNotEmpty() || FirebaseApp.initializeApp(appContext) != null
    } catch (_: Exception) {
        false
    }

    fun safeFirebaseAuth(context: Context? = null): FirebaseAuth? = try {
        if (context != null && !isFirebaseConfigured(context)) {
            null
        } else {
            Firebase.auth
        }
    } catch (_: IllegalStateException) {
        null
    } catch (_: Exception) {
        null
    }

    fun safeAuthStateFlow(context: Context): Flow<FirebaseUser?> {
        val auth = safeFirebaseAuth(context) ?: return flowOf(null)
        return auth.authStateFlow()
    }

    fun resolveDefaultWebClientId(context: Context): String? = runCatching {
        val resId = context.resources.getIdentifier(
            "default_web_client_id",
            "string",
            context.packageName
        )
        if (resId == 0) null else context.getString(resId).takeIf { it.isNotBlank() }
    }.getOrNull()

    tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

    fun mapGoogleSignInException(error: Throwable, language: AppLanguage): String = when (error) {
        is NoCredentialException -> if (language == AppLanguage.BN) {
            "ডিভাইসে সক্রিয় কোনো Google অ্যাকাউন্ট পাওয়া যায়নি বা অ্যাপের SHA-1/OAuth কনফিগারেশন মেলেনি। ডিভাইসে Google অ্যাকাউন্ট লগইন আছে কিনা এবং Firebase-এ রিলিজ SHA-1 ও Google Sign-In চালু আছে কিনা যাচাই করুন।"
        } else {
            "No eligible Google account credentials were found on this device, or the app's SHA-1/Web OAuth client in Firebase does not match this build."
        }
        is GetCredentialProviderConfigurationException -> if (language == AppLanguage.BN) {
            "এই ডিভাইসে Google Play Services Credential Manager পাওয়া যায়নি বা কনফিগারেশন অসম্পূর্ণ।"
        } else {
            "Google Credential Manager provider is unavailable or misconfigured on this device (check Google Play Services)."
        }
        is GetCredentialUnsupportedException -> if (language == AppLanguage.BN) {
            "এই ডিভাইস বা সিস্টেম সংস্করণে Credential Manager সমর্থিত নয়।"
        } else {
            "Credential Manager is not supported on this device."
        }
        is GetCredentialException -> AuthValidator.mapFirebaseException(error, language)
        else -> AuthValidator.mapFirebaseException(error, language)
    }

    fun attemptAutoSignIn(
        context: Context,
        credentialManager: CredentialManager,
        scope: CoroutineScope,
        onAuthSuccess: () -> Unit,
        onUnauthenticated: () -> Unit
    ) {
        val auth = safeFirebaseAuth(context)
        if (auth == null) {
            onUnauthenticated()
            return
        }
        if (auth.currentUser != null) {
            onAuthSuccess()
            return
        }
        val clientId = resolveDefaultWebClientId(context)
        if (clientId == null) {
            onUnauthenticated()
            return
        }
        val targetContext = context.findActivity() ?: context
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(true)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
        scope.launch {
            try {
                val cred = credentialManager.getCredential(targetContext, request).credential
                if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                    auth.signInWithCredential(
                        GoogleAuthProvider.getCredential(token, null)
                    ).await()
                    onAuthSuccess()
                } else {
                    onUnauthenticated()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                onUnauthenticated()
            }
        }
    }

    private suspend fun requestInteractiveGoogleCredential(
        activity: Activity,
        credentialManager: CredentialManager,
        clientId: String
    ) = try {
        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val primaryRequest = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()
        credentialManager.getCredential(activity, primaryRequest).credential
    } catch (noCred: NoCredentialException) {
        Log.w(AUTH_TAG, "GetSignInWithGoogleOption returned NoCredentialException; falling back to GetGoogleIdOption", noCred)
        val fallbackOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(false)
            .build()
        val fallbackRequest = GetCredentialRequest.Builder()
            .addCredentialOption(fallbackOption)
            .build()
        credentialManager.getCredential(activity, fallbackRequest).credential
    }

    fun onGoogleSignInClicked(
        context: Context,
        credentialManager: CredentialManager,
        scope: CoroutineScope,
        language: AppLanguage = AppLanguage.BN,
        onAuthSuccess: () -> Unit,
        onAuthError: (String) -> Unit,
        onAuthCancelled: () -> Unit = {}
    ) {
        val auth = safeFirebaseAuth(context)
        if (auth == null) {
            onAuthError(
                if (language == AppLanguage.BN) {
                    "Firebase কনফিগারেশন (google-services.json) পাওয়া যায়নি।"
                } else {
                    "Firebase configuration is missing for this build."
                }
            )
            return
        }
        val clientId = resolveDefaultWebClientId(context)
        if (clientId == null) {
            onAuthError(
                if (language == AppLanguage.BN) {
                    "Google Sign-In Web OAuth Client (default_web_client_id) কনফিগার করা নেই।"
                } else {
                    "Google Sign-In configuration missing: default_web_client_id was not generated."
                }
            )
            return
        }
        val activity = context.findActivity()
        if (activity == null) {
            onAuthError(
                if (language == AppLanguage.BN) {
                    "Google Sign-In চালু করার জন্য সক্রিয় Activity পাওয়া যায়নি।"
                } else {
                    "Activity context is required for Google Sign-In."
                }
            )
            return
        }
        scope.launch {
            try {
                val cred = requestInteractiveGoogleCredential(activity, credentialManager, clientId)
                if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                    auth.signInWithCredential(
                        GoogleAuthProvider.getCredential(token, null)
                    ).await()
                    onAuthSuccess()
                } else {
                    onAuthError(
                        if (language == AppLanguage.BN) {
                            "Google Sign-In থেকে অপ্রত্যাশিত ক্রেডেনশিয়াল পাওয়া গেছে।"
                        } else {
                            "Unexpected credential type returned by Google Sign-In."
                        }
                    )
                }
            } catch (e: GetCredentialCancellationException) {
                Log.w(AUTH_TAG, "Google Sign-In cancelled or dismissed: ${e.message}", e)
                onAuthCancelled()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(AUTH_TAG, "Google Sign-In failed", e)
                onAuthError(mapGoogleSignInException(e, language))
            }
        }
    }

    suspend fun registerWithEmailPassword(
        context: Context,
        fullName: String,
        email: String,
        password: String,
        language: AppLanguage
    ): AuthActionOutcome {
        val auth = safeFirebaseAuth(context)
            ?: return AuthActionOutcome.Error(
                if (language == AppLanguage.BN) {
                    "Firebase কনফিগারেশন (google-services.json) যুক্ত করা হয়নি।"
                } else {
                    "Firebase configuration (app/google-services.json) is missing."
                }
            )
        return try {
            val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
            val user = result.user ?: return AuthActionOutcome.Error("Account creation failed.")
            val cleanName = fullName.trim()
            if (cleanName.isNotEmpty()) {
                runCatching {
                    user.updateProfile(
                        UserProfileChangeRequest.Builder()
                            .setDisplayName(cleanName)
                            .build()
                    ).await()
                }
            }
            val verificationSent = runCatching {
                user.sendEmailVerification().await()
                true
            }.getOrDefault(false)

            AuthActionOutcome.Success(
                uid = user.uid,
                email = user.email.orEmpty(),
                displayName = cleanName.ifBlank { user.displayName.orEmpty() },
                isEmailVerified = user.isEmailVerified,
                verificationEmailSent = verificationSent
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(AUTH_TAG, "Email registration failed", e)
            AuthActionOutcome.Error(AuthValidator.mapFirebaseException(e, language))
        }
    }

    suspend fun signInWithEmailPassword(
        context: Context,
        email: String,
        password: String,
        language: AppLanguage
    ): AuthActionOutcome {
        val auth = safeFirebaseAuth(context)
            ?: return AuthActionOutcome.Error(
                if (language == AppLanguage.BN) {
                    "Firebase কনফিগারেশন (google-services.json) যুক্ত করা হয়নি।"
                } else {
                    "Firebase configuration (app/google-services.json) is missing."
                }
            )
        return try {
            val result = auth.signInWithEmailAndPassword(email.trim(), password).await()
            val user = result.user ?: return AuthActionOutcome.Error("Login failed.")
            AuthActionOutcome.Success(
                uid = user.uid,
                email = user.email.orEmpty(),
                displayName = user.displayName.orEmpty(),
                isEmailVerified = user.isEmailVerified
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(AUTH_TAG, "Email login failed", e)
            AuthActionOutcome.Error(AuthValidator.mapFirebaseException(e, language))
        }
    }

    suspend fun sendPasswordResetEmail(
        context: Context,
        email: String,
        language: AppLanguage
    ): Result<Unit> {
        val auth = safeFirebaseAuth(context)
            ?: return Result.failure(
                IllegalStateException(
                    if (language == AppLanguage.BN) "Firebase কনফিগারেশন অনুপস্থিত।" else "Firebase not configured."
                )
            )
        return try {
            auth.sendPasswordResetEmail(email.trim()).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(IllegalStateException(AuthValidator.mapFirebaseException(e, language)))
        }
    }

    suspend fun resendEmailVerification(
        context: Context,
        language: AppLanguage
    ): Result<Unit> {
        val user = safeFirebaseAuth(context)?.currentUser
            ?: return Result.failure(IllegalStateException("No authenticated user."))
        return try {
            user.sendEmailVerification().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(IllegalStateException(AuthValidator.mapFirebaseException(e, language)))
        }
    }

    suspend fun reloadCurrentUser(context: Context): Result<Boolean> {
        val user = safeFirebaseAuth(context)?.currentUser
            ?: return Result.failure(IllegalStateException("No authenticated user."))
        return try {
            user.reload().await()
            Result.success(user.isEmailVerified)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateFirebaseDisplayName(context: Context, displayName: String): Result<Unit> {
        val user = safeFirebaseAuth(context)?.currentUser ?: return Result.success(Unit)
        return try {
            user.updateProfile(
                UserProfileChangeRequest.Builder()
                    .setDisplayName(displayName.trim())
                    .build()
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun reauthenticateWithPassword(
        context: Context,
        password: String,
        language: AppLanguage
    ): Result<Unit> {
        val user = safeFirebaseAuth(context)?.currentUser
            ?: return Result.failure(IllegalStateException("Not signed in."))
        val email = user.email
            ?: return Result.failure(IllegalStateException("Account has no email credential."))
        return try {
            user.reauthenticate(EmailAuthProvider.getCredential(email, password)).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(IllegalStateException(AuthValidator.mapFirebaseException(e, language)))
        }
    }

    suspend fun reauthenticateWithGoogle(
        activity: Activity,
        credentialManager: CredentialManager,
        language: AppLanguage
    ): Result<Unit> {
        val user = safeFirebaseAuth(activity)?.currentUser
            ?: return Result.failure(IllegalStateException("Not signed in."))
        val clientId = resolveDefaultWebClientId(activity)
            ?: return Result.failure(IllegalStateException("Missing default_web_client_id"))
        return try {
            val cred = requestInteractiveGoogleCredential(activity, credentialManager, clientId)
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                user.reauthenticate(GoogleAuthProvider.getCredential(token, null)).await()
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException("Invalid Google credential."))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(IllegalStateException(mapGoogleSignInException(e, language)))
        }
    }

    suspend fun deleteCurrentAccount(
        context: Context,
        language: AppLanguage
    ): Result<Unit> {
        val user = safeFirebaseAuth(context)?.currentUser
            ?: return Result.failure(IllegalStateException("No authenticated user."))
        return try {
            user.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(IllegalStateException(AuthValidator.mapFirebaseException(e, language)))
        }
    }

    fun signOut(
        context: Context? = null,
        credentialManager: CredentialManager,
        scope: CoroutineScope,
        onBeforeSignOut: suspend () -> Unit = {},
        onSignOutComplete: () -> Unit = {}
    ) {
        scope.launch {
            withContext(NonCancellable) {
                try {
                    onBeforeSignOut()
                    credentialManager.clearCredentialState(ClearCredentialStateRequest())
                } catch (e: Exception) {
                    Log.w(AUTH_TAG, "Failed to clear credential state: ${e.message}")
                } finally {
                    safeFirebaseAuth(context)?.signOut()
                    onSignOutComplete()
                }
            }
        }
    }
}
