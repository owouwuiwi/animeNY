package mihon.data.extension.service

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.extension.anime.model.AnimeExtension
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.okio.decodeFromBufferedSource
import kotlinx.serialization.protobuf.ProtoBuf
import logcat.LogPriority
import mihon.data.extension.model.NetworkAnimeExtensionStore
import mihon.data.extension.model.NetworkLegacyExtension
import mihon.data.extension.model.NetworkLegacyExtensionRepo
import mihon.domain.extension.model.ExtensionStore
import okhttp3.HttpUrl.Companion.toHttpUrl
import tachiyomi.core.common.util.system.logcat
import kotlin.coroutines.cancellation.CancellationException
import mihon.domain.extension.model.ContentWarning as DomainContentWarning

@Inject
@SingleIn(AppScope::class)
class AnimeExtensionStoreService(
    private val network: NetworkHelper,
    private val json: Json,
    private val protoBuf: ProtoBuf,
) {
    suspend fun fetch(indexUrl: String): Result<ExtensionStore> {
        return fetch(indexUrl, forceV2 = false)
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
                        source.decodeExtensionIndexObject<NetworkAnimeExtensionStore>(format, json, protoBuf)
                    }

                    ExtensionIndexFormat.PROTOBUF ->
                        source.decodeExtensionIndexObject<NetworkAnimeExtensionStore>(format, json, protoBuf)
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

    suspend fun getExtensions(store: ExtensionStore): Result<List<AnimeExtension.Available>> {
        return try {
            val extensions = if (!store.isLegacy) {
                store.readModernExtensionList<NetworkAnimeExtensionStore, NetworkAnimeExtensionStore.ExtensionList>(json, protoBuf) {
                    network.client.newCall(GET(it)).awaitSuccess()
                }?.let { toAvailableExtensions(it, store) }.orEmpty()
            } else {
                val storeBaseUrl = store.indexUrl.removeSuffix("/repo.json")
                val response = network.client.newCall(GET("$storeBaseUrl/index.min.json")).awaitSuccess()
                response.readExtensionIndex { source, _ ->
                    json.decodeFromBufferedSource<List<NetworkLegacyExtension>>(source)
                        .filter { isAnimePackage(it.pkg) }
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
        extensionList: NetworkAnimeExtensionStore.ExtensionList,
        store: ExtensionStore,
    ): List<AnimeExtension.Available> {
        return extensionList.extensions
            .filter { isAnimePackage(it.packageName) }
            .map { extension ->
                val lang = extension.sources.map { it.language }.toSet()
                AnimeExtension.Available(
                    name = extension.name.substringAfter(
                        "Aniyomi: ",
                    ).substringAfter("Animetail: ").substringAfter("Tachiyomi: "),
                    pkgName = extension.packageName,
                    apkUrl = extension.resources.apkUrl,
                    iconUrl = extension.resources.iconUrl,
                    libVersion = extension.extensionLib.toDoubleOrNull() ?: 0.0,
                    versionCode = extension.versionCode,
                    versionName = extension.versionName,
                    lang = if (lang.size == 1) lang.first() else "all",
                    contentWarning = when (extension.contentWarning) {
                        NetworkAnimeExtensionStore.ContentWarning.SAFE -> DomainContentWarning.SAFE
                        NetworkAnimeExtensionStore.ContentWarning.MIXED -> DomainContentWarning.MIXED
                        NetworkAnimeExtensionStore.ContentWarning.NSFW -> DomainContentWarning.NSFW
                        else -> DomainContentWarning.SAFE
                    },
                    isTorrent = extension.isTorrent,
                    sources = extension.sources.map { source ->
                        AnimeExtension.Available.AnimeSource(
                            id = source.id,
                            name = source.name,
                            lang = source.language,
                            baseUrl = source.homeUrl,
                        )
                    },
                    store = store,
                    signatureHash = "NO_SIGNING_KEY",
                    repoName = store.name,
                )
            }
    }

    private fun toAvailableExtension(
        netExt: NetworkLegacyExtension,
        store: ExtensionStore,
        storeBaseUrl: String,
    ): AnimeExtension.Available {
        return AnimeExtension.Available(
            name = netExt.name.substringAfter("Aniyomi: ").substringAfter("Animetail: ").substringAfter("Tachiyomi: "),
            pkgName = netExt.pkg,
            apkUrl = "$storeBaseUrl/apk/${netExt.apk}",
            iconUrl = "$storeBaseUrl/icon/${netExt.pkg}.png",
            libVersion = runCatching { netExt.version.substringBeforeLast('.').toDouble() }.getOrElse {
                runCatching { netExt.version.substringBefore('.').toDouble() }.getOrDefault(0.0)
            },
            versionCode = netExt.code,
            versionName = netExt.version,
            lang = netExt.lang,
            contentWarning = if (netExt.nsfw == 1) DomainContentWarning.NSFW else DomainContentWarning.SAFE,
            isTorrent = netExt.torrent == 1,
            sources = run {
                val sources = netExt.sources
                if (sources.isNullOrEmpty()) {
                    listOf(
                        AnimeExtension.Available.AnimeSource(
                            id = 0,
                            name = netExt.name,
                            lang = netExt.lang,
                            baseUrl = "",
                        ),
                    )
                } else {
                    sources.map { source ->
                        AnimeExtension.Available.AnimeSource(
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

    private fun isAnimePackage(pkgName: String): Boolean {
        return pkgName.contains("animeextension") || pkgName.contains(".anime.")
    }
}
