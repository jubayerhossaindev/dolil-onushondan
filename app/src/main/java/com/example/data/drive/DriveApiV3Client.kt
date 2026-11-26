package com.example.data.drive

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.random.Random

class DriveHttpException(
    val statusCode: Int,
    override val message: String
) : IOException(message)

interface DriveRemoteStorage {
    suspend fun generateFileIds(accessToken: String, count: Int = 2): List<String>

    suspend fun listFilesInFolder(
        accessToken: String,
        parentFolderId: String?,
        nameEquals: String? = null,
        mimeTypeEquals: String? = null
    ): List<DriveFileMetadata>

    suspend fun getFileMetadata(
        accessToken: String,
        fileId: String
    ): DriveFileMetadata?

    suspend fun findOrCreateFolder(
        accessToken: String,
        folderName: String,
        parentFolderId: String?
    ): DriveFileMetadata

    suspend fun uploadJsonResumable(
        accessToken: String,
        preGeneratedOrExistingId: String?,
        fileName: String,
        parentFolderId: String,
        jsonBytes: ByteArray,
        isUpdateExisting: Boolean
    ): DriveFileMetadata

    suspend fun downloadFileBytes(
        accessToken: String,
        fileId: String
    ): ByteArray

    suspend fun deleteFile(
        accessToken: String,
        fileId: String
    )
}

class HttpDriveApiV3Client(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .build()
) : DriveRemoteStorage {

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val genIdsAdapter = moshi.adapter(GenerateIdsResponse::class.java)
    private val fileListAdapter = moshi.adapter(DriveFileListResponse::class.java)
    private val fileDtoAdapter = moshi.adapter(DriveFileDto::class.java)
    private val createMetadataAdapter = moshi.adapter(CreateDriveFileMetadata::class.java)

    override suspend fun generateFileIds(accessToken: String, count: Int): List<String> =
        generateIdsInternal(accessToken, count)

    // Helper cleanly avoids double-reading OkHttp body
    private inline fun <T> callAndParse(request: Request, parser: (String) -> T): T {
        client.newCall(request).execute().use { resp ->
            val rawBody = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw DriveHttpException(resp.code, "Drive HTTP ${resp.code}: ${rawBody.take(300)}")
            }
            return parser(rawBody)
        }
    }

    override suspend fun listFilesInFolder(
        accessToken: String,
        parentFolderId: String?,
        nameEquals: String?,
        mimeTypeEquals: String?
    ): List<DriveFileMetadata> {
        val results = mutableListOf<DriveFileMetadata>()
        var pageToken: String? = null
        val clauses = mutableListOf("trashed = false")
        if (!parentFolderId.isNullOrBlank()) {
            clauses.add("'${escapeQuery(parentFolderId)}' in parents")
        } else {
            clauses.add("'root' in parents")
        }
        if (!nameEquals.isNullOrBlank()) {
            clauses.add("name = '${escapeQuery(nameEquals)}'")
        }
        if (!mimeTypeEquals.isNullOrBlank()) {
            clauses.add("mimeType = '${escapeQuery(mimeTypeEquals)}'")
        }
        val query = clauses.joinToString(" and ")

        do {
            val page = executeWithRetry {
                val urlBuilder = "$DRIVE_BASE/files".toHttpUrl().newBuilder()
                    .addQueryParameter("q", query)
                    .addQueryParameter("spaces", "drive")
                    .addQueryParameter("pageSize", "100")
                    .addQueryParameter(
                        "fields",
                        "nextPageToken,files(id,name,parents,mimeType,size,modifiedTime,sha256Checksum)"
                    )
                if (!pageToken.isNullOrBlank()) {
                    urlBuilder.addQueryParameter("pageToken", pageToken)
                }
                val req = Request.Builder()
                    .url(urlBuilder.build())
                    .header("Authorization", "Bearer $accessToken")
                    .get()
                    .build()
                callAndParse(req) { raw ->
                    fileListAdapter.fromJson(raw) ?: DriveFileListResponse()
                }
            }
            results.addAll(page.files.map { it.toMetadata() })
            pageToken = page.nextPageToken?.takeIf { it.isNotBlank() }
        } while (pageToken != null)

        return results
    }

    override suspend fun getFileMetadata(
        accessToken: String,
        fileId: String
    ): DriveFileMetadata? = executeWithRetry {
        val safeFileId = validateDriveId(fileId, "fileId")
        val url = "$DRIVE_BASE/files".toHttpUrl().newBuilder()
            .addPathSegment(safeFileId)
            .addQueryParameter("fields", "id,name,parents,mimeType,size,modifiedTime,sha256Checksum,trashed")
            .build()
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (resp.code == 404) return@executeWithRetry null
            if (!resp.isSuccessful) {
                throw DriveHttpException(resp.code, "Drive metadata HTTP ${resp.code}: ${raw.take(250)}")
            }
            val dto = fileDtoAdapter.fromJson(raw) ?: return@executeWithRetry null
            if (dto.trashed == true) null else dto.toMetadata()
        }
    }

    override suspend fun findOrCreateFolder(
        accessToken: String,
        folderName: String,
        parentFolderId: String?
    ): DriveFileMetadata {
        val existing = listFilesInFolder(
            accessToken = accessToken,
            parentFolderId = parentFolderId,
            nameEquals = folderName,
            mimeTypeEquals = DriveSchemaConstants.MIME_FOLDER
        )
        if (existing.isNotEmpty()) {
            return existing.first()
        }

        val generatedId = runCatching { generateIdsInternal(accessToken, 1).firstOrNull() }.getOrNull()
        val metadata = CreateDriveFileMetadata(
            id = generatedId,
            name = folderName,
            mimeType = DriveSchemaConstants.MIME_FOLDER,
            parents = if (parentFolderId.isNullOrBlank()) null else listOf(parentFolderId)
        )
        val jsonBody = createMetadataAdapter.toJson(metadata).toRequestBody(JSON_MEDIA_TYPE)
        return executeWithRetry {
            val url = "$DRIVE_BASE/files".toHttpUrl().newBuilder()
                .addQueryParameter("fields", "id,name,parents,mimeType,size,modifiedTime,sha256Checksum")
                .build()
            val req = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .post(jsonBody)
                .build()
            client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (resp.code == 409 && generatedId != null) {
                    val already = getFileMetadata(accessToken, generatedId)
                    if (already != null) return@executeWithRetry already
                }
                if (!resp.isSuccessful) {
                    throw DriveHttpException(resp.code, "Folder create HTTP ${resp.code}: ${raw.take(250)}")
                }
                (fileDtoAdapter.fromJson(raw) ?: throw IOException("Empty folder metadata")).toMetadata()
            }
        }
    }

    override suspend fun uploadJsonResumable(
        accessToken: String,
        preGeneratedOrExistingId: String?,
        fileName: String,
        parentFolderId: String,
        jsonBytes: ByteArray,
        isUpdateExisting: Boolean
    ): DriveFileMetadata {
        val metadataJson = if (isUpdateExisting) {
            createMetadataAdapter.toJson(
                CreateDriveFileMetadata(
                    id = null,
                    name = fileName,
                    mimeType = DriveSchemaConstants.MIME_JSON,
                    parents = null
                )
            )
        } else {
            createMetadataAdapter.toJson(
                CreateDriveFileMetadata(
                    id = preGeneratedOrExistingId,
                    name = fileName,
                    mimeType = DriveSchemaConstants.MIME_JSON,
                    parents = listOf(parentFolderId)
                )
            )
        }

        val initOutcome = executeWithRetry {
            val initUrl = if (isUpdateExisting && !preGeneratedOrExistingId.isNullOrBlank()) {
                val safeId = validateDriveId(preGeneratedOrExistingId, "preGeneratedOrExistingId")
                "$UPLOAD_BASE/files".toHttpUrl().newBuilder()
                    .addPathSegment(safeId)
                    .addQueryParameter("uploadType", "resumable")
                    .addQueryParameter("fields", "id,name,parents,mimeType,size,modifiedTime,sha256Checksum")
                    .build()
            } else {
                "$UPLOAD_BASE/files".toHttpUrl().newBuilder()
                    .addQueryParameter("uploadType", "resumable")
                    .addQueryParameter("fields", "id,name,parents,mimeType,size,modifiedTime,sha256Checksum")
                    .build()
            }
            val initReqBuilder = Request.Builder()
                .url(initUrl)
                .header("Authorization", "Bearer $accessToken")
                .header("X-Upload-Content-Type", DriveSchemaConstants.MIME_JSON)
                .header("X-Upload-Content-Length", jsonBytes.size.toString())

            val body = metadataJson.toRequestBody(JSON_MEDIA_TYPE)
            val initReq = if (isUpdateExisting) {
                initReqBuilder.patch(body).build()
            } else {
                initReqBuilder.post(body).build()
            }

            client.newCall(initReq).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (resp.code == 409 && !isUpdateExisting && !preGeneratedOrExistingId.isNullOrBlank()) {
                    val existing = getFileMetadata(accessToken, preGeneratedOrExistingId)
                    if (existing != null && existing.sizeBytes == jsonBytes.size.toLong()) {
                        return@executeWithRetry ResumableInitResult.AlreadyUploaded(existing)
                    }
                }
                if (!resp.isSuccessful) {
                    throw DriveHttpException(resp.code, "Resumable init HTTP ${resp.code}: ${raw.take(250)}")
                }
                val location = resp.header("Location")
                    ?: throw IOException("Drive resumable upload missing Location header")
                ResumableInitResult.SessionUrl(location)
            }
        }

        val sessionUrl = when (initOutcome) {
            is ResumableInitResult.AlreadyUploaded -> return initOutcome.meta
            is ResumableInitResult.SessionUrl -> initOutcome.url
        }

        return executeWithRetry {
            val putReq = Request.Builder()
                .url(sessionUrl)
                .header("Authorization", "Bearer $accessToken")
                .header("Content-Type", DriveSchemaConstants.MIME_JSON)
                .put(jsonBytes.toRequestBody(JSON_MEDIA_TYPE))
                .build()
            client.newCall(putReq).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (resp.code == 409 && !isUpdateExisting && !preGeneratedOrExistingId.isNullOrBlank()) {
                    val existing = getFileMetadata(accessToken, preGeneratedOrExistingId)
                    if (existing != null) return@executeWithRetry existing
                }
                if (!resp.isSuccessful) {
                    throw DriveHttpException(resp.code, "Resumable upload PUT HTTP ${resp.code}: ${raw.take(250)}")
                }
                val dto = fileDtoAdapter.fromJson(raw)
                    ?: throw IOException("Upload response missing file metadata")
                dto.toMetadata()
            }
        }
    }

    override suspend fun downloadFileBytes(
        accessToken: String,
        fileId: String
    ): ByteArray = executeWithRetry {
        val safeFileId = validateDriveId(fileId, "fileId")
        val url = "$DRIVE_BASE/files".toHttpUrl().newBuilder()
            .addPathSegment(safeFileId)
            .addQueryParameter("alt", "media")
            .build()
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                val err = resp.body?.string().orEmpty()
                throw DriveHttpException(resp.code, "Download HTTP ${resp.code}: ${err.take(250)}")
            }
            resp.body?.bytes() ?: ByteArray(0)
        }
    }

    override suspend fun deleteFile(
        accessToken: String,
        fileId: String
    ) {
        val safeFileId = validateDriveId(fileId, "fileId")
        val url = "$DRIVE_BASE/files".toHttpUrl().newBuilder()
            .addPathSegment(safeFileId)
            .build()
        executeWithRetry {
            val req = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .delete()
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.code == 404) return@executeWithRetry
                if (!resp.isSuccessful) {
                    val err = resp.body?.string().orEmpty()
                    throw DriveHttpException(resp.code, "Delete HTTP ${resp.code}: ${err.take(250)}")
                }
            }
        }
    }

    private fun validateDriveId(id: String, fieldName: String): String {
        val trimmed = id.trim()
        require(trimmed.isNotEmpty()) { "$fieldName must not be blank" }
        require(
            !trimmed.contains('/') &&
                !trimmed.contains('\\') &&
                !trimmed.contains('?') &&
                !trimmed.contains('#') &&
                !trimmed.contains("..") &&
                trimmed.none { it.isWhitespace() || it.isISOControl() }
        ) { "Invalid characters in $fieldName" }
        return trimmed
    }

    private suspend fun generateIdsInternal(accessToken: String, count: Int): List<String> =
        executeWithRetry {
            val url = "$DRIVE_BASE/files/generateIds".toHttpUrl().newBuilder()
                .addQueryParameter("count", count.coerceIn(1, 10).toString())
                .addQueryParameter("space", "drive")
                .build()
            val req = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()
            callAndParse(req) { raw ->
                genIdsAdapter.fromJson(raw)?.ids.orEmpty()
            }
        }



    private suspend fun <T> executeWithRetry(
        maxAttempts: Int = 4,
        block: suspend () -> T
    ): T = withContext(Dispatchers.IO) {
        var attempt = 0
        var lastError: Throwable? = null
        while (attempt < maxAttempts) {
            try {
                return@withContext block()
            } catch (e: DriveHttpException) {
                lastError = e
                val retryable = e.statusCode == 429 || e.statusCode in 500..599
                if (!retryable || attempt == maxAttempts - 1) throw e
            } catch (e: IOException) {
                lastError = e
                if (attempt == maxAttempts - 1) throw e
            }
            val baseDelay = 300L * (1L shl attempt)
            val jitter = Random.nextLong(50L, 250L)
            delay(min(baseDelay + jitter, 5000L))
            attempt++
        }
        throw (lastError ?: IOException("Drive request failed"))
    }

    private fun escapeQuery(raw: String): String =
        raw.replace("\\", "\\\\").replace("'", "\\'")

    companion object {
        private const val DRIVE_BASE = "https://www.googleapis.com/drive/v3"
        private const val UPLOAD_BASE = "https://www.googleapis.com/upload/drive/v3"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

private data class CreateDriveFileMetadata(
    val id: String? = null,
    val name: String,
    val mimeType: String,
    val parents: List<String>? = null
)

private data class GenerateIdsResponse(
    val ids: List<String> = emptyList()
)

private data class DriveFileListResponse(
    val nextPageToken: String? = null,
    val files: List<DriveFileDto> = emptyList()
)

private data class DriveFileDto(
    val id: String = "",
    val name: String = "",
    val parents: List<String> = emptyList(),
    val mimeType: String = "",
    val size: String? = null,
    val modifiedTime: String? = null,
    val sha256Checksum: String? = null,
    val trashed: Boolean? = null
) {
    fun toMetadata() = DriveFileMetadata(
        id = id,
        name = name,
        parents = parents,
        mimeType = mimeType,
        sizeBytes = size?.toLongOrNull() ?: 0L,
        modifiedTimeUtc = modifiedTime.orEmpty(),
        sha256Checksum = sha256Checksum
    )
}

private sealed interface ResumableInitResult {
    data class SessionUrl(val url: String) : ResumableInitResult
    data class AlreadyUploaded(val meta: DriveFileMetadata) : ResumableInitResult
}
