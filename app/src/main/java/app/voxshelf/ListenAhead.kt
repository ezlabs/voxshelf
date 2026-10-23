package app.voxshelf

import android.content.Context
import android.net.ConnectivityManager
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Saves the chapter you're on and the next one or two to the phone while you listen, so playback carries on
 * through tunnels and dead spots. Two chapters ahead on Wi-Fi, one on mobile data, and none on mobile data when
 * "Download only on Wi-Fi" is on (the chapter still streams). Saved chapters are cleared when you finish a book.
 */
@OptIn(UnstableApi::class)
class ListenAhead(private val context: Context) {
    private val downloads = Downloads.get(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var writer: CacheWriter? = null
    private var planned: List<String> = emptyList()

    /** Call when playback starts or moves to another chapter. */
    fun update(player: Player) {
        val metered = context.getSystemService(ConnectivityManager::class.java)?.isActiveNetworkMetered ?: true
        val ahead = when {
            !metered -> 2
            downloads.wifiOnly -> -1
            else -> 1
        }
        val first = player.currentMediaItemIndex
        val urls = (first..minOf(first + ahead, player.mediaItemCount - 1))
            .mapNotNull { player.getMediaItemAt(it).localConfiguration?.uri?.toString() }
        if (urls == planned && job?.isActive == true) return
        planned = urls
        job?.cancel()
        writer?.cancel()
        job = scope.launch {
            for (url in urls) {
                if (!isActive) break
                if (downloads.isDownloaded(url)) continue
                val w = CacheWriter(downloads.listenSource.createDataSourceForDownloading(), DataSpec(url.toUri()), null, null)
                writer = w
                // Network trouble just leaves the chapter to stream; the next chapter change tries again.
                runCatching { w.cache() }.onFailure { android.util.Log.w("ListenAhead", "couldn't save $url", it) }
            }
        }
    }

    /** Clears a finished book's saved chapters from the listening store (real downloads are kept). */
    fun clear(player: Player) {
        val urls = (0 until player.mediaItemCount).mapNotNull { player.getMediaItemAt(it).localConfiguration?.uri?.toString() }
        job?.cancel()
        writer?.cancel()
        planned = emptyList()
        scope.launch { urls.forEach { runCatching { downloads.listenCache.removeResource(it) } } }
    }

    fun release() {
        writer?.cancel()
        scope.cancel()
    }
}
