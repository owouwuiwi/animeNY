package eu.kanade.tachiyomi.data.sync.service

import android.content.Context
import eu.kanade.domain.sync.SyncPreferences
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.sync.SyncNotifier
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.PUT
import eu.kanade.tachiyomi.network.await
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import logcat.LogPriority
import logcat.logcat
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.apache.http.HttpStatus
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.TimeUnit

class SyncYomiSyncService(
    context: Context,
    json: Json,
    syncPreferences: SyncPreferences,
    private val notifier: SyncNotifier,

    private val protoBuf: ProtoBuf = Injekt.get(),
) : SyncService(context, json, syncPreferences) {

    private class SyncYomiException(message: String, cause: Throwable? = null) : Exception(message, cause)

    @Suppress("TooGenericExceptionCaught")
    override suspend fun doSync(syncData: SyncData): SyncUpload? {
        return try {
            val url = syncPreferences.syncYomiEndpoint()
            val apiKey = syncPreferences.clientAPIKey().get()
            val scope = syncAccountScope(url, apiKey)
            val provider = SyncReceipt.Provider.SYNCYOMI
            val lastETag = provider.appliedRevision(syncPreferences, scope)
            val (remoteData, etag) = pullSyncData(url, apiKey, lastETag)

            val finalSyncData = if (remoteData != null) {
                assert(etag.isNotEmpty()) { "ETag should never be empty if remote data is not null" }
                logcat(LogPriority.DEBUG, "SyncService") {
                    "Try update remote data with ETag($etag)"
                }
                mergeSyncData(syncData, remoteData)
            } else {
                // init or overwrite remote data
                logcat(LogPriority.DEBUG) {
                    "Try overwrite remote data with ETag($etag)"
                }
                syncData
            }
            val backup = finalSyncData.backup ?: return null
            pushSyncData(url, apiKey, backup, etag)?.let {
                SyncUpload(backup, SyncReceipt(provider, scope, it))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "Error syncing: ${e.message}" }
            notifier.showSyncError(e.message)
            null
        }
    }

    @Suppress("ReturnCount")
    private suspend fun pullSyncData(url: String, apiKey: String, lastETag: String): Pair<SyncData?, String> {
        val headersBuilder = Headers.Builder().add("X-API-Token", apiKey)
        if (lastETag != "") {
            headersBuilder.add("If-None-Match", lastETag)
        }
        val headers = headersBuilder.build()

        val downloadRequest = GET(
            url = url,
            headers = headers,
        )

        val client = OkHttpClient()
        client.newCall(downloadRequest).await().use { response ->
            if (response.code == HttpStatus.SC_NOT_MODIFIED) {
                if (lastETag.isEmpty()) throw SyncYomiException("Received an unexpected HTTP 304")
                logcat(LogPriority.INFO) { "Remote server not modified" }
                return Pair(null, lastETag)
            } else if (response.code == HttpStatus.SC_NOT_FOUND) {
                return Pair(null, "")
            }

            if (!response.isSuccessful) {
                throw SyncYomiException("Failed to download sync data: HTTP ${response.code}")
            }
            val newETag = response.headers["ETag"]
                .takeIf { it?.isNotEmpty() == true } ?: throw SyncYomiException("Missing ETag")
            try {
                val backup = protoBuf.decodeFromByteArray(Backup.serializer(), response.body.bytes())
                return Pair(SyncData(backup = backup), newETag)
            } catch (e: SerializationException) {
                throw SyncYomiException("The remote backup could not be read", e)
            }
        }
    }

    @Suppress("MagicNumber")
    private suspend fun pushSyncData(url: String, apiKey: String, backup: Backup, eTag: String): String? {
        val timeout = 30L

        val headersBuilder = Headers.Builder().add("X-API-Token", apiKey)
        if (eTag.isNotEmpty()) {
            headersBuilder.add("If-Match", eTag)
        }
        val headers = headersBuilder.build()

        // Set timeout to 30 seconds
        val client = OkHttpClient.Builder()
            .connectTimeout(timeout, TimeUnit.SECONDS)
            .readTimeout(timeout, TimeUnit.SECONDS)
            .writeTimeout(timeout, TimeUnit.SECONDS)
            .build()

        val byteArray = protoBuf.encodeToByteArray(Backup.serializer(), backup)
        if (byteArray.isEmpty()) {
            throw IllegalStateException(context.stringResource(MR.strings.empty_backup_error))
        }
        val body = byteArray.toRequestBody("application/octet-stream".toMediaType())

        val uploadRequest = PUT(
            url = url,
            headers = headers,
            body = body,
        )

        client.newCall(uploadRequest).await().use { response ->
            if (response.code == HttpStatus.SC_PRECONDITION_FAILED) {
                logcat(LogPriority.DEBUG) { "Remote backup changed during sync; will try again on the next sync" }
                return null
            }
            if (!response.isSuccessful) {
                throw SyncYomiException("Failed to upload sync data: HTTP ${response.code}")
            }
            val newETag = response.headers["ETag"]
                .takeIf { it?.isNotEmpty() == true } ?: throw SyncYomiException("Missing ETag")
            logcat(LogPriority.DEBUG) { "SyncYomi sync completed" }
            return newETag
        }
    }
}
