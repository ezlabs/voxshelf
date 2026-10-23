package app.voxshelf

import android.app.Notification
import android.content.Context
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.PlatformScheduler
import androidx.media3.exoplayer.scheduler.Requirements
import androidx.media3.exoplayer.scheduler.Scheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

/** Where a book's download stands. */
sealed interface BookDownload {
    data object None : BookDownload
    data class Downloading(val percent: Int) : BookDownload
    data object WaitingForWifi : BookDownload
    data object Done : BookDownload
    data object Failed : BookDownload
}

/**
 * Saves books to the phone for offline listening. Each chapter is one download, keyed like the player's
 * media ids ("bookId/chapter"), and the player reads saved chapters before going to the internet.
 */
@OptIn(UnstableApi::class)
class Downloads private constructor(private val context: Context) {
    private val database = StandaloneDatabaseProvider(context)
    val cache = SimpleCache(File(context.filesDir, "audio"), NoOpCacheEvictor(), database)
    val http: DefaultHttpDataSource.Factory = DefaultHttpDataSource.Factory().setUserAgent("Voxshelf/0.1 (https://voxshelf.app; hello@voxshelf.app)")

    val manager = DownloadManager(context, database, cache, http, Executors.newFixedThreadPool(2)).apply {
        maxParallelDownloads = 2
        requirements = requirementsFor(wifiOnly)
    }

    /** When on, downloads wait for Wi-Fi (or any network that isn't metered). Off by default. */
    var wifiOnly: Boolean
        get() = prefs.getBoolean("wifiOnly", false)
        set(value) {
            prefs.edit().putBoolean("wifiOnly", value).apply()
            DownloadService.sendSetRequirements(context, VoxDownloadService::class.java, requirementsFor(value), false)
        }

    private val prefs get() = context.getSharedPreferences("downloads", Context.MODE_PRIVATE)

    private fun requirementsFor(wifiOnly: Boolean) =
        Requirements(if (wifiOnly) Requirements.NETWORK_UNMETERED else Requirements.NETWORK)

    /** Plays saved chapters from the phone and streams the rest without saving them. */
    val playerSource: CacheDataSource.Factory = CacheDataSource.Factory()
        .setCache(cache)
        .setUpstreamDataSourceFactory(http)
        .setCacheWriteDataSinkFactory(null)

    fun download(book: Book) = book.chapters.forEachIndexed { i, chapter ->
        val request = DownloadRequest.Builder(mediaId(book.id, i), chapter.url.toUri()).build()
        DownloadService.sendAddDownload(context, VoxDownloadService::class.java, request, false)
    }

    fun remove(bookId: Int) = manager.currentDownloads.map { it.request.id }.plus(savedIds(bookId)).toSet()
        .filter { bookIdOf(it) == bookId }
        .forEach { DownloadService.sendRemoveDownload(context, VoxDownloadService::class.java, it, false) }

    suspend fun status(bookId: Int): BookDownload = withContext(Dispatchers.IO) {
        val chapters = buildList {
            manager.downloadIndex.getDownloads().use { while (it.moveToNext()) if (bookIdOf(it.download.request.id) == bookId) add(it.download) }
        }
        val live = manager.currentDownloads.associateBy { it.request.id }
        val all = chapters.map { live[it.request.id] ?: it }
        when {
            all.isEmpty() -> BookDownload.None
            all.all { it.state == Download.STATE_COMPLETED } -> BookDownload.Done
            all.any { it.state == Download.STATE_FAILED } -> BookDownload.Failed
            manager.notMetRequirements and Requirements.NETWORK_UNMETERED != 0 &&
                all.none { it.state == Download.STATE_DOWNLOADING } -> BookDownload.WaitingForWifi
            else -> BookDownload.Downloading(
                (all.sumOf { if (it.state == Download.STATE_COMPLETED) 100.0 else it.percentDownloaded.coerceAtLeast(0f).toDouble() } / all.size).toInt()
            )
        }
    }

    private fun savedIds(bookId: Int) = buildList {
        manager.downloadIndex.getDownloads().use { while (it.moveToNext()) add(it.download.request.id) }
    }.filter { bookIdOf(it) == bookId }

    companion object {
        @Volatile private var instance: Downloads? = null
        fun get(context: Context) = instance ?: synchronized(this) {
            instance ?: Downloads(context.applicationContext).also { instance = it }
        }
    }
}

/** Keeps downloads going in the background, with a progress notification. */
@OptIn(UnstableApi::class)
class VoxDownloadService : DownloadService(1, DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL, CHANNEL, R.string.downloads_channel, 0) {
    private val notifications by lazy { DownloadNotificationHelper(this, CHANNEL) }

    override fun getDownloadManager() = Downloads.get(this).manager

    // Lets Android restart waiting downloads (for example when Wi-Fi comes back) even if the app is closed.
    override fun getScheduler(): Scheduler = PlatformScheduler(this, JOB_ID)

    override fun getForegroundNotification(downloads: MutableList<Download>, notMetRequirements: Int): Notification =
        notifications.buildProgressNotification(this, android.R.drawable.stat_sys_download, null, getString(R.string.downloading), downloads, notMetRequirements)

    private companion object {
        const val JOB_ID = 1
        const val CHANNEL = "downloads"
    }
}
