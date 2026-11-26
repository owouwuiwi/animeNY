package eu.kanade.tachiyomi.data.connections.discord

import android.app.Activity
import android.content.Context
import kotlinx.coroutines.CoroutineScope

internal interface DiscordPresence {
    val lastUsedScreen: DiscordScreen
    fun setEngineActivity(activity: Activity?)
    fun initialize(context: Context)
    fun observePreferences(context: Context, scope: CoroutineScope)
    fun start(context: Context)
    fun stop(context: Context, delay: Long = 30000L)
    suspend fun setScreen(context: Context, discordScreen: DiscordScreen)
    suspend fun setAnimeScreen(context: Context, discordScreen: DiscordScreen, playerData: PlayerData = PlayerData())
    suspend fun setMangaScreen(context: Context, discordScreen: DiscordScreen, readerData: ReaderData = ReaderData())
    suspend fun setPlayerActivity(context: Context, playerData: PlayerData = PlayerData())
    suspend fun setReaderActivity(context: Context, readerData: ReaderData = ReaderData())
}
