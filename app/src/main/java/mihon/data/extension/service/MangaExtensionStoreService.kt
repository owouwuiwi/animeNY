package mihon.data.extension.service

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.extension.manga.model.MangaExtension
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.okio.decodeFromBufferedSource
import kotlinx.serialization.protobuf.ProtoBuf
import logcat.LogPriority
import mihon.data.extension.model.NetworkExtensionStore
import mihon.data.extension.model.NetworkLegacyExtension
import mihon.data.extension.model.NetworkLegacyExtensionRepo
import mihon.domain.extension.model.ExtensionStore
import okhttp3.HttpUrl.Companion.toHttpUrl
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import kotlin.coroutines.cancellation.CancellationException
import mihon.domain.extension.model.ContentWarning as DomainContentWarning

@Inject
@SingleIn(AppScope::class)
class MangaExtensionStoreService(
    private val network: NetworkHelper,
    private val json: Json,
    private val protoBuf: ProtoBuf,
) {
    suspend fun fetch(indexUrl: String): Result<ExtensionStore> = withIOContext {
        fetch(indexUrl, forceV2 = false)
    }

    private suspend fun fetch(indexUrl: String, forceV2: Boolean): Result<ExtensionStore> {
        var updatedIndexUrl: String = indexUrl
        return try {
            val response = network.client.newCall(GET(indexUrl)).awaitSuccess()
            var responseUrl = response.request.url
            val store = response.readExtensionIndex { source, format ->
                val networkStore = when (format) {
                    ExtensionIndexFormat.JSON_ARRAY -> {
                        val listUrl = responseUrl.takeIf { it.pathSegments.last() == "index.min.json" }
                            ?: indexUrl.toHttpUrl().takeIf { it.pathSegments.last() == "index.min.json" }
                            ?: throw IllegalArgumentException("The legacy store URL must point to index.min.json.")
                        updatedIndexUrl = checkNotNull(listUrl.resolve("repo.json")).toString()
                        val metadata = network.client.newCall(GET(updatedIndexUrl)).awaitSuccess()
                        responseUrl = metadata.request.url
                        metadata.readExtensionIndex { index, _ ->
                            json.decodeFromBufferedSource<NetworkLegacyExtensionRepo>(index)
                        }
                    }

                    ExtensionIndexFormat.JSON_OBJECT -> try {
                        source.peek().use { json.decodeFromBufferedSource<NetworkLegacyExtensionRepo>(it) }
                    } catch (_: SerializationException) {
                        source.decodeExtensionIndexObject<NetworkExtensionStore>(format, json, protoBuf)
                    }

                    ExtensionIndexFormat.PROTOBUF ->
                        source.decodeExtensionIndexObject<NetworkExtensionStore>(format, json, protoBuf)
                }

                if (networkStore is NetworkLegacyExtensionRepo) {
                    val indexV2 = networkStore.indexV2
                    if (indexV2 != null) {
                        require(!forceV2) { "The version 2 index redirected to another legacy index." }
                        val v2Url = requireNotNull(responseUrl.resolve(indexV2)) { "Invalid version 2 index URL." }
                        return fetch(v2Url.toString(), forceV2 = true)
                    }
                }

                val decodedStore = networkStore.toExtensionStore(updatedIndexUrl)
                val listUrl = decodedStore.extensionListUrl?.let(responseUrl::resolveExtensionListUrl)
                decodedStore.copy(extensionListUrl = listUrl)
            }
            Result.success(store)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) {
                "Failed to add extension store '$updatedIndexUrl'"
            }
            Result.failure(e)
        }
    }

    suspend fun getExtensions(store: ExtensionStore): Result<List<MangaExtension.Available>> = withIOContext {
        try {
            val extensions = if (!store.isLegacy) {
                store.readModernExtensionList<NetworkExtensionStore, NetworkExtensionStore.ExtensionList>(
                    json,
                    protoBuf,
                ) {
                    network.client.newCall(GET(it)).awaitSuccess()
                }?.let { toAvailableExtensions(it, store) }.orEmpty()
            } else {
                val storeBaseUrl = store.indexUrl.removeSuffix("/repo.json")
                val response = network.client.newCall(GET("$storeBaseUrl/index.min.json")).awaitSuccess()
                response.readExtensionIndex { source, _ ->
                    json.decodeFromBufferedSource<List<NetworkLegacyExtension>>(source)
                        .filter { isMangaPackage(it.pkg) }
                        .map { toAvailableExtension(it, store, storeBaseUrl) }
                }
            }
            Result.success(extensions)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun toAvailableExtensions(
        extensionList: NetworkExtensionStore.ExtensionList,
        store: ExtensionStore,
    ): List<MangaExtension.Available> {
        return extensionList.extensions
            .filter { isMangaPackage(it.packageName) }
            .map { extension ->
                val lang = extension.sources.map { it.language }.toSet()
                MangaExtension.Available(
                    name = extension.name,
                    pkgName = extension.packageName,
                    apkUrl = extension.resources.apkUrl,
                    iconUrl = extension.resources.iconUrl,
                    libVersion = extension.extensionLib.toDouble(),
                    versionCode = extension.versionCode,
                    versionName = extension.versionName,
                    lang = if (lang.size == 1) lang.first() else "all",
                    contentWarning = when (extension.contentWarning) {
                        NetworkExtensionStore.ContentWarning.SAFE -> DomainContentWarning.SAFE
                        NetworkExtensionStore.ContentWarning.MIXED -> DomainContentWarning.MIXED
                        NetworkExtensionStore.ContentWarning.NSFW -> DomainContentWarning.NSFW
                        else -> DomainContentWarning.SAFE
                    },
                    sources = extension.sources.map { source ->
                        MangaExtension.Available.MangaSource(
                            id = source.id,
                            name = source.name,
                            lang = source.language,
                            baseUrl = source.homeUrl,
                        )
                    },
                    store = store,
                    signatureHash = "NO_SIGNING_KEY", // Will be filled/verified on load/trust
                    repoName = store.name,
                )
            }
    }

    private fun toAvailableExtension(
        netExt: NetworkLegacyExtension,
        store: ExtensionStore,
        storeBaseUrl: String,
    ): MangaExtension.Available {
        return MangaExtension.Available(
            name = netExt.name.substringAfter("Tachiyomi: "),
            pkgName = netExt.pkg,
            apkUrl = "$storeBaseUrl/apk/${netExt.apk}",
            iconUrl = "$storeBaseUrl/icon/${netExt.pkg}.png",
            libVersion = netExt.version.substringBeforeLast('.').toDouble(),
            versionCode = netExt.code,
            versionName = netExt.version,
            lang = netExt.lang,
            contentWarning = if (netExt.nsfw == 1) DomainContentWarning.NSFW else DomainContentWarning.SAFE,
            sources = run {
                val sources = netExt.sources
                if (sources.isNullOrEmpty()) {
                    listOf(
                        MangaExtension.Available.MangaSource(
                            id = 0,
                            name = netExt.name,
                            lang = netExt.lang,
                            baseUrl = "",
                        ),
                    )
                } else {
                    sources.map { source ->
                        MangaExtension.Available.MangaSource(
                            id = source.id,
                            name = source.name,
                            lang = source.lang,
                            baseUrl = source.baseUrl,
                        )
                    }
                }
            },
            store = store,
            signatureHash = "NO_SIGNING_KEY",
            repoName = store.name,
        )
    }

    private fun isMangaPackage(pkgName: String): Boolean {
        return !pkgName.contains("animeextension") && !pkgName.contains(".anime.")
    }
}
