package com.example.data.drive

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class DriveCryptoEngine(
    private val defaultIterations: Int = MIN_PBKDF2_ITERATIONS
) {
    private val secureRandom = SecureRandom()

    init {
        require(defaultIterations >= 10_000) {
            "PBKDF2 iterations too low"
        }
    }

    fun canonicalAad(
        accountNamespaceId: String,
        driveSubjectId: String,
        backupId: String,
        deviceId: String,
        sequenceNumber: Long,
        schemaVersion: Int
    ): String = "dolil-v1|ns=$accountNamespaceId|sub=$driveSubjectId|backup=$backupId|dev=$deviceId|seq=$sequenceNumber|schema=$schemaVersion"

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    suspend fun sealPayload(
        plaintextBytes: ByteArray,
        backupId: String,
        sequenceNumber: Long,
        timestampUtc: String,
        deviceId: String,
        accountNamespaceId: String,
        driveSubjectId: String,
        schemaVersion: Int,
        passphrase: CharArray?
    ): DriveBackupEnvelope = withContext(Dispatchers.Default) {
        val plaintextChecksum = sha256Hex(plaintextBytes)
        if (passphrase == null || passphrase.isEmpty()) {
            return@withContext DriveBackupEnvelope(
                backupId = backupId,
                sequenceNumber = sequenceNumber,
                schemaVersion = schemaVersion,
                timestampUtc = timestampUtc,
                deviceId = deviceId,
                accountNamespaceId = accountNamespaceId,
                driveSubjectId = driveSubjectId,
                encrypted = false,
                encryptionMetadata = null,
                payloadSha256 = plaintextChecksum,
                payloadBase64 = Base64.encodeToString(plaintextBytes, Base64.NO_WRAP)
            )
        }

        val salt = ByteArray(SALT_BYTES).also { secureRandom.nextBytes(it) }
        val iv = ByteArray(GCM_IV_BYTES).also { secureRandom.nextBytes(it) }
        val aad = canonicalAad(
            accountNamespaceId = accountNamespaceId,
            driveSubjectId = driveSubjectId,
            backupId = backupId,
            deviceId = deviceId,
            sequenceNumber = sequenceNumber,
            schemaVersion = schemaVersion
        )

        val keyBytes = deriveKeyBytes(passphrase, salt, defaultIterations)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val keySpec = SecretKeySpec(keyBytes, "AES")
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.updateAAD(aad.toByteArray(Charsets.UTF_8))
            val ciphertext = cipher.doFinal(plaintextBytes)
            val metadata = DriveEncryptionMetadata(
                algorithm = "AES-256-GCM",
                kdf = "PBKDF2WithHmacSHA256",
                iterations = defaultIterations,
                saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP),
                ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP),
                aadCanonical = aad,
                keyVersion = 1
            )
            DriveBackupEnvelope(
                backupId = backupId,
                sequenceNumber = sequenceNumber,
                schemaVersion = schemaVersion,
                timestampUtc = timestampUtc,
                deviceId = deviceId,
                accountNamespaceId = accountNamespaceId,
                driveSubjectId = driveSubjectId,
                encrypted = true,
                encryptionMetadata = metadata,
                payloadSha256 = plaintextChecksum,
                payloadBase64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
            )
        } finally {
            keyBytes.fill(0)
        }
    }

    suspend fun unsealPayload(
        envelope: DriveBackupEnvelope,
        passphrase: CharArray?
    ): Result<ByteArray> = withContext(Dispatchers.Default) {
        if (!envelope.encrypted) {
            return@withContext runCatching {
                val decoded = Base64.decode(envelope.payloadBase64, Base64.DEFAULT)
                val actualSha = sha256Hex(decoded)
                require(actualSha == envelope.payloadSha256) {
                    "Backup payload checksum mismatch"
                }
                decoded
            }
        }

        val meta = envelope.encryptionMetadata
            ?: return@withContext Result.failure(IllegalStateException("Missing encryption metadata"))
        if (passphrase == null || passphrase.isEmpty()) {
            return@withContext Result.failure(PassphraseMissingException(envelope.backupId))
        }

        val expectedAad = canonicalAad(
            accountNamespaceId = envelope.accountNamespaceId,
            driveSubjectId = envelope.driveSubjectId,
            backupId = envelope.backupId,
            deviceId = envelope.deviceId,
            sequenceNumber = envelope.sequenceNumber,
            schemaVersion = envelope.schemaVersion
        )
        if (meta.aadCanonical != expectedAad) {
            return@withContext Result.failure(SecurityException("Envelope AAD binding mismatch"))
        }

        return@withContext runCatching {
            val salt = Base64.decode(meta.saltBase64, Base64.DEFAULT)
            val iv = Base64.decode(meta.ivBase64, Base64.DEFAULT)
            val ciphertext = Base64.decode(envelope.payloadBase64, Base64.DEFAULT)
            val keyBytes = deriveKeyBytes(passphrase, salt, meta.iterations)
            try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                val keySpec = SecretKeySpec(keyBytes, "AES")
                cipher.init(Cipher.DECRYPT_MODE, keySpec, GCMParameterSpec(GCM_TAG_BITS, iv))
                cipher.updateAAD(expectedAad.toByteArray(Charsets.UTF_8))
                val plaintext = cipher.doFinal(ciphertext)
                val actualSha = sha256Hex(plaintext)
                if (actualSha != envelope.payloadSha256) {
                    throw SecurityException("Decrypted payload checksum verification failed")
                }
                plaintext
            } finally {
                keyBytes.fill(0)
            }
        }
    }

    private fun deriveKeyBytes(
        passphrase: CharArray,
        salt: ByteArray,
        iterations: Int
    ): ByteArray {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(passphrase, salt, iterations, KEY_BITS)
        return try {
            factory.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    companion object {
        const val MIN_PBKDF2_ITERATIONS = 600_000
        private const val SALT_BYTES = 16
        private const val GCM_IV_BYTES = 12
        private const val GCM_TAG_BITS = 128
        private const val KEY_BITS = 256
    }
}

class PassphraseMissingException(val backupId: String) :
    IllegalStateException("Backup passphrase required for $backupId")
