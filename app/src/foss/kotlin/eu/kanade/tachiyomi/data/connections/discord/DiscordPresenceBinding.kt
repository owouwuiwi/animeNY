package eu.kanade.tachiyomi.data.connections.discord

import android.app.Activity
import android.content.Context
import kotlinx.coroutines.CoroutineScope

internal const val DISCORD_PRESENCE_AVAILABLE = false

internal val discordPresence: DiscordPresence get() = NoDiscordPresence

private object NoDiscordPresence : DiscordPresence {
    override val lastUsedScreen = DiscordScreen.APP
    override fun setEngineActivity(activity: Activity?) = Unit
    override fun initialize(context: Context) = Unit
    override fun observePreferences(context: Context, scope: CoroutineScope) = Unit
    override fun start(context: Context) = Unit
    override fun stop(context: Context, delay: Long) = Unit
    override suspend fun setScreen(context: Context, discordScreen: DiscordScreen) = Unit
    override suspend fun setAnimeScreen(context: Context, discordScreen: DiscordScreen, playerData: PlayerData) = Unit
    override suspend fun setMangaScreen(context: Context, discordScreen: DiscordScreen, readerData: ReaderData) = Unit
    override suspend fun setPlayerActivity(context: Context, playerData: PlayerData) = Unit
    override suspend fun setReaderActivity(context: Context, readerData: ReaderData) = Unit
}
