package com.example.core.config

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.example.core.security.AuthManager
import com.example.data.local.DeviceIdentityStore
import com.google.firebase.FirebaseApp
import java.security.MessageDigest

enum class ReadinessStage(
    val titleEn: String,
    val titleBn: String
) {
    LOCAL_CONFIGURATION_COMPLETE(
        "Local Configuration Complete",
        "স্থানীয় কনফিগারেশন সম্পন্ন"
    ),
    FIREBASE_AUTH_TESTED(
        "Firebase Authentication Tested",
        "Firebase অথেনটিকেশন যাচাইকৃত"
    ),
    DRIVE_AUTH_TESTED(
        "Google Drive Authorization Tested",
        "Google Drive অনুমোদন যাচাইকৃত"
    ),
    BACKUP_RESTORE_TESTED(
        "Backup & Restore Tested",
        "ব্যাকআপ ও পুনরুদ্ধার যাচাইকৃত"
    ),
    EXTERNAL_OAUTH_VERIFICATION(
        "External OAuth Consent Verification Required",
        "বাহ্যিক OAuth কনসেন্ট যাচাই প্রয়োজন"
    ),
    PRODUCTION_RELEASE_APPROVED(
        "Production Release Approved",
        "প্রোডাকশন রিলিজ অনুমোদিত"
    )
}

enum class ReadinessState(
    val labelEn: String,
    val labelBn: String
) {
    READY("Complete", "সম্পন্ন"),
    PARTIAL("Configured (Pending Live Test)", "কনফিগারড (লাইভ টেস্ট বাকি)"),
    MISSING("Pending / Action Required", "বাকি / করণীয় রয়েছে")
}

data class ReadinessStageStatus(
    val stage: ReadinessStage,
    val state: ReadinessState,
    val detailEn: String,
    val detailBn: String
)

data class CredentialReadinessReport(
    val packageName: String,
    val hasFirebaseApp: Boolean,
    val hasDefaultWebClientId: Boolean,
    val hasDriveLinked: Boolean,
    val hasVerifiedDriveBackup: Boolean,
    val signingSha1: String,
    val signingSha256: String,
    val stages: List<ReadinessStageStatus>
) {
    val allLiveReady: Boolean
        get() = hasFirebaseApp && hasDefaultWebClientId && hasDriveLinked && hasVerifiedDriveBackup

    val firebaseAuthReady: Boolean
        get() = hasFirebaseApp && hasDefaultWebClientId
}

object CredentialReadinessChecker {

    fun inspect(
        context: Context,
        firebaseUid: String?,
        hasVerifiedDriveTokenThisSession: Boolean = false
    ): CredentialReadinessReport {
        val appContext = context.applicationContext
        val hasFirebase = try {
            FirebaseApp.getApps(appContext).isNotEmpty() || FirebaseApp.initializeApp(appContext) != null
        } catch (_: Exception) {
            false
        }
        val webClientId = AuthManager.resolveDefaultWebClientId(appContext)
        val hasWebClientId = !webClientId.isNullOrBlank()

        val identityStore = DeviceIdentityStore(appContext)
        val link = firebaseUid?.takeIf { it.isNotBlank() }?.let { identityStore.getDriveLink(it) }
        val hasDriveLinked = link != null && link.driveSubjectId.isNotBlank()
        val hasVerifiedBackup = link != null && !link.lastVerifiedBackupId.isNullOrBlank()

        val (sha1, sha256) = readSigningFingerprints(appContext)

        val stages = listOf(
            ReadinessStageStatus(
                stage = ReadinessStage.LOCAL_CONFIGURATION_COMPLETE,
                state = ReadinessState.READY,
                detailEn = "Room UID isolation, Drive v3 client, PBKDF2-AES-256-GCM crypto, and native AuthorizationClient code are configured without embedded client secrets.",
                detailBn = "Room UID আইসোলেশন, Drive v3 ক্লায়েন্ট, PBKDF2-AES-256-GCM এনক্রিপশন ও নেটিভ AuthorizationClient সেটআপ সম্পন্ন (কোনো Client Secret APK-তে নেই)।"
            ),
            ReadinessStageStatus(
                stage = ReadinessStage.FIREBASE_AUTH_TESTED,
                state = when {
                    hasFirebase && hasWebClientId && !firebaseUid.isNullOrBlank() -> ReadinessState.READY
                    hasFirebase && hasWebClientId -> ReadinessState.PARTIAL
                    else -> ReadinessState.MISSING
                },
                detailEn = when {
                    hasFirebase && hasWebClientId && !firebaseUid.isNullOrBlank() ->
                        "Firebase Auth initialized and active session verified."
                    hasFirebase && hasWebClientId ->
                        "google-services.json & default_web_client_id detected; run live Email/Google sign-in on device."
                    else ->
                        "Add app/google-services.json with SHA-1/SHA-256 and Web OAuth client (default_web_client_id) in Firebase Console."
                },
                detailBn = when {
                    hasFirebase && hasWebClientId && !firebaseUid.isNullOrBlank() ->
                        "Firebase Auth সক্রিয় এবং বর্তমান সেশন যাচাই হয়েছে।"
                    hasFirebase && hasWebClientId ->
                        "google-services.json পাওয়া গেছে; ডিভাইসে Email ও Google লগইন পরীক্ষা করুন।"
                    else ->
                        "Firebase Console থেকে app/google-services.json যুক্ত করুন এবং default_web_client_id ও SHA-1/SHA-256 কনফিগার করুন।"
                }
            ),
            ReadinessStageStatus(
                stage = ReadinessStage.DRIVE_AUTH_TESTED,
                state = if (hasDriveLinked) ReadinessState.READY else ReadinessState.MISSING,
                detailEn = if (hasDriveLinked) {
                    "Personal Google Drive connected and bound to OIDC sub (${link.driveEmail.ifBlank { link.driveSubjectId }})."
                } else {
                    "Authorize personal Google Drive via AuthorizationClient (drive.file, openid, profile, email) on a physical/Play Services device."
                },
                detailBn = if (hasDriveLinked) {
                    "ব্যক্তিগত Google Drive সংযুক্ত এবং OIDC sub-এর সাথে আবদ্ধ (${link.driveEmail.ifBlank { link.driveSubjectId }})।"
                } else {
                    "Google Play Services-যুক্ত ডিভাইসে ব্যক্তিগত Google Drive (drive.file) অনুমোদন করুন।"
                }
            ),
            ReadinessStageStatus(
                stage = ReadinessStage.BACKUP_RESTORE_TESTED,
                state = if (hasVerifiedBackup) ReadinessState.READY else ReadinessState.MISSING,
                detailEn = if (hasVerifiedBackup) {
                    "Verified snapshot ${link.lastVerifiedBackupId} recorded in commit graph."
                } else {
                    "Run a live backup snapshot and restore test against your connected Google Drive account."
                },
                detailBn = if (hasVerifiedBackup) {
                    "সর্বশেষ যাচাইকৃত ব্যাকআপ: ${link.lastVerifiedBackupId}"
                } else {
                    "সংযুক্ত Google Drive-এ অন্তত একটি লাইভ ব্যাকআপ ও পুনরুদ্ধার পরীক্ষা সম্পন্ন করুন।"
                }
            ),
            ReadinessStageStatus(
                stage = ReadinessStage.EXTERNAL_OAUTH_VERIFICATION,
                state = ReadinessState.MISSING,
                detailEn = "drive.file is a recommended non-sensitive per-file scope, but external OAuth consent branding/publishing still requires verification in Google Cloud Console before public distribution.",
                detailBn = "drive.file নিরাপদ প্রতি-ফাইল স্কোপ হলেও পাবলিক রিলিজের আগে Google Cloud Console-এ OAuth Consent Screen ও ব্র্যান্ডিং যাচাই প্রয়োজন।"
            ),
            ReadinessStageStatus(
                stage = ReadinessStage.PRODUCTION_RELEASE_APPROVED,
                state = ReadinessState.MISSING,
                detailEn = "Register release keystore SHA-1/SHA-256 fingerprints in Firebase & Cloud Console and finalize Play release signing.",
                detailBn = "রিলিজ কী-স্টোরের SHA-1 ও SHA-256 ফিঙ্গারপ্রিন্ট Firebase ও Cloud Console-এ যুক্ত করে রিলিজ অনুমোদন সম্পন্ন করুন।"
            )
        )

        return CredentialReadinessReport(
            packageName = appContext.packageName,
            hasFirebaseApp = hasFirebase,
            hasDefaultWebClientId = hasWebClientId,
            hasDriveLinked = hasDriveLinked,
            hasVerifiedDriveBackup = hasVerifiedBackup,
            signingSha1 = sha1,
            signingSha256 = sha256,
            stages = stages
        )
    }

    @Suppress("DEPRECATION")
    private fun readSigningFingerprints(context: Context): Pair<String, String> = runCatching {
        val pm = context.packageManager
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            val signingInfo = info.signingInfo
            if (signingInfo?.hasMultipleSigners() == true) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo?.signingCertificateHistory
            }
        } else {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
        }
        val certBytes = signatures?.firstOrNull()?.toByteArray() ?: return@runCatching "" to ""
        digestHex(certBytes, "SHA-1") to digestHex(certBytes, "SHA-256")
    }.getOrDefault("" to "")

    private fun digestHex(bytes: ByteArray, algorithm: String): String {
        val digest = MessageDigest.getInstance(algorithm).digest(bytes)
        return digest.joinToString(":") { "%02X".format(it) }
    }
}
