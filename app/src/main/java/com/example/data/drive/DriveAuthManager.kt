package com.example.data.drive

import android.accounts.Account
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

interface DriveAuthGateway {
    suspend fun authorizeSilently(
        context: Context,
        targetAccountEmail: String? = null
    ): DriveAuthResult

    suspend fun requestInteractiveAuthorization(
        context: Context,
        targetAccountEmail: String? = null,
        forceSelectAccount: Boolean = false
    ): DriveAuthResult

    suspend fun handleResolutionResult(
        context: Context,
        resultData: Intent?
    ): DriveAuthResult

    suspend fun revokeGoogleAccess(
        context: Context,
        lastAccessToken: String? = null
    ): Result<Unit>
}

class PlayServicesDriveAuthGateway(
    private val okHttpClient: OkHttpClient = OkHttpClient()
) : DriveAuthGateway {

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val userInfoAdapter = moshi.adapter(GoogleUserInfoDto::class.java)

    override suspend fun authorizeSilently(
        context: Context,
        targetAccountEmail: String?
    ): DriveAuthResult = executeAuthorize(
        context = context,
        targetAccountEmail = targetAccountEmail,
        forceSelectAccount = false
    )

    override suspend fun requestInteractiveAuthorization(
        context: Context,
        targetAccountEmail: String?,
        forceSelectAccount: Boolean
    ): DriveAuthResult = executeAuthorize(
        context = context,
        targetAccountEmail = if (forceSelectAccount) null else targetAccountEmail,
        forceSelectAccount = forceSelectAccount
    )

    override suspend fun handleResolutionResult(
        context: Context,
        resultData: Intent?
    ): DriveAuthResult {
        if (resultData == null) {
            return DriveAuthResult.Cancelled("No authorization intent result returned.")
        }
        return try {
            val authResult = Identity.getAuthorizationClient(context)
                .getAuthorizationResultFromIntent(resultData)
            resolveAuthorizedToken(authResult)
        } catch (e: ApiException) {
            DriveAuthResult.Error(
                message = e.localizedMessage ?: "Google Drive authorization failed (${e.statusCode})",
                isRevokedOrExpired = e.statusCode == 4 || e.statusCode == 5
            )
        } catch (e: Exception) {
            DriveAuthResult.Error(e.localizedMessage ?: "Google Drive authorization failed")
        }
    }

    override suspend fun revokeGoogleAccess(
        context: Context,
        lastAccessToken: String?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val tokenToRevoke = lastAccessToken?.takeIf { it.isNotBlank() }
                ?: runCatching {
                    val silent = Identity.getAuthorizationClient(context)
                        .authorize(buildAuthorizationRequest(null, forceSelectAccount = false))
                        .await()
                    if (!silent.hasResolution()) silent.accessToken else null
                }.getOrNull()

            if (!tokenToRevoke.isNullOrBlank()) {
                val formBody = FormBody.Builder()
                    .add("token", tokenToRevoke)
                    .build()
                val req = Request.Builder()
                    .url(OAUTH_REVOKE_URL)
                    .post(formBody)
                    .build()
                okHttpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful && resp.code != 400) {
                        throw IOException("OAuth revocation HTTP ${resp.code}")
                    }
                }
            }
            Unit
        }
    }

    private suspend fun executeAuthorize(
        context: Context,
        targetAccountEmail: String?,
        forceSelectAccount: Boolean
    ): DriveAuthResult {
        return try {
            val request = buildAuthorizationRequest(
                targetAccountEmail = targetAccountEmail,
                forceSelectAccount = forceSelectAccount
            )
            val result = Identity.getAuthorizationClient(context)
                .authorize(request)
                .await()

            if (result.hasResolution()) {
                val pending = result.pendingIntent
                    ?: return DriveAuthResult.Error("Authorization requires consent, but PendingIntent was null.", isRevokedOrExpired = true)
                DriveAuthResult.NeedsResolution(pending)
            } else {
                resolveAuthorizedToken(result)
            }
        } catch (e: ApiException) {
            DriveAuthResult.Error(
                message = e.localizedMessage ?: "Drive authorization failed (${e.statusCode})",
                isRevokedOrExpired = e.statusCode == 4 || e.statusCode == 16
            )
        } catch (e: Exception) {
            DriveAuthResult.Error(e.localizedMessage ?: "Drive authorization failed")
        }
    }

    private fun buildAuthorizationRequest(
        targetAccountEmail: String?,
        forceSelectAccount: Boolean
    ): AuthorizationRequest {
        val builder = AuthorizationRequest.builder()
            .setRequestedScopes(REQUIRED_SCOPES)
        if (!forceSelectAccount && !targetAccountEmail.isNullOrBlank()) {
            builder.setAccount(Account(targetAccountEmail, "com.google"))
        }
        return builder.build()
    }

    private suspend fun resolveAuthorizedToken(result: AuthorizationResult): DriveAuthResult {
        val token = result.accessToken
        if (token.isNullOrBlank()) {
            return DriveAuthResult.Error("Access token was empty.", isRevokedOrExpired = true)
        }
        val userInfoRes = fetchUserInfo(token)
        return userInfoRes.fold(
            onSuccess = { info ->
                DriveAuthResult.Authorized(accessToken = token, accountInfo = info)
            },
            onFailure = { err ->
                DriveAuthResult.Error(
                    message = err.localizedMessage ?: "Failed to verify Google account identity (userinfo).",
                    isRevokedOrExpired = true
                )
            }
        )
    }

    private suspend fun fetchUserInfo(accessToken: String): Result<DriveAccountInfo> =
        withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder()
                    .url(USERINFO_URL)
                    .header("Authorization", "Bearer $accessToken")
                    .get()
                    .build()
                okHttpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        throw IOException("UserInfo HTTP ${resp.code}")
                    }
                    val body = resp.body?.string().orEmpty()
                    val dto = userInfoAdapter.fromJson(body)
                        ?: throw IOException("Empty userinfo JSON")
                    val sub = dto.sub?.trim().orEmpty()
                    require(sub.isNotBlank()) {
                        "Google userinfo response missing stable subject identifier (sub)."
                    }
                    DriveAccountInfo(
                        subjectId = sub,
                        email = dto.email.orEmpty().trim(),
                        displayName = dto.name.orEmpty().trim()
                    )
                }
            }
        }

    companion object {
        const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
        private const val USERINFO_URL = "https://www.googleapis.com/oauth2/v3/userinfo"
        private const val OAUTH_REVOKE_URL = "https://oauth2.googleapis.com/revoke"

        val REQUIRED_SCOPES = listOf(
            Scope(DRIVE_FILE_SCOPE),
            Scope("openid"),
            Scope("profile"),
            Scope("email")
        )
    }
}

private data class GoogleUserInfoDto(
    val sub: String? = null,
    val email: String? = null,
    val name: String? = null
)
