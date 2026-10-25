package app.voxshelf

import android.content.Context
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaConstants
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.guava.future

/** The chapters of a book as player items, with the details shown on the lock screen and in the car. */
fun chapterItems(book: Book, cover: Art?): List<MediaItem> = book.chapters.mapIndexed { i, chapter ->
    MediaItem.Builder()
        .setMediaId(mediaId(book.id, i))
        .setUri(chapter.url.toUri())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(chapter.title)
                .setArtist(book.author)
                .setAlbumTitle(book.title)
                .setArtworkUri(cover?.fast?.toUri())
                .build()
        )
        .build()
}

/**
 * What Android Auto shows: four tabs (Listening, To listen, Downloaded, Popular) of books as a grid of covers.
 * Picking a book, or asking for one by voice, starts it where you left off.
 */
@OptIn(UnstableApi::class)
class CarLibrary(private val context: Context) : MediaLibrarySession.Callback {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val catalog = Catalog.get(context)
    private val library = Library.get(context)

    private enum class Tab(val id: String, val title: String) {
        Listening("listening", "Listening"),
        ToListen("to-listen", "To listen"),
        Downloaded("downloaded", "Downloaded"),
        Popular("popular", "Popular"),
    }

    private val gridStyle = Bundle().apply {
        putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM)
        putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE, MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM)
    }

    override fun onGetLibraryRoot(
        session: MediaLibrarySession, browser: MediaSession.ControllerInfo, params: LibraryParams?,
    ): ListenableFuture<LibraryResult<MediaItem>> = Futures.immediateFuture(
        LibraryResult.ofItem(folder(ROOT, "Voxshelf"), LibraryParams.Builder().setExtras(gridStyle).build())
    )

    override fun onGetChildren(
        session: MediaLibrarySession, browser: MediaSession.ControllerInfo, parentId: String,
        page: Int, pageSize: Int, params: LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future {
        val items = when (parentId) {
            ROOT -> Tab.entries.map { folder(it.id, it.title) }
            Tab.Listening.id -> library.entries(Status.Listening).mapNotNull { catalog.recording(it.recordingId) }.map(::bookItem)
            Tab.ToListen.id -> library.entries(Status.Want).mapNotNull { catalog.recording(it.recordingId) }.map(::bookItem)
            Tab.Downloaded.id -> Downloads.get(context).bookIds().mapNotNull { catalog.recording(it) }.map(::bookItem)
            Tab.Popular.id -> catalog.collection(Collection.Popular, 40).map { bookItem(it.first) }
            else -> emptyList()
        }
        LibraryResult.ofItemList(items.take(MAX_ITEMS), params)
    }

    override fun onGetItem(
        session: MediaLibrarySession, browser: MediaSession.ControllerInfo, mediaId: String,
    ): ListenableFuture<LibraryResult<MediaItem>> = scope.future {
        val recording = recordingIdOf(mediaId)?.let { catalog.recording(it) }
        if (recording != null) LibraryResult.ofItem(bookItem(recording), null)
        else LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
    }

    override fun onSearch(
        session: MediaLibrarySession, browser: MediaSession.ControllerInfo, query: String, params: LibraryParams?,
    ): ListenableFuture<LibraryResult<Void>> = scope.future {
        session.notifySearchResultChanged(browser, query, catalog.search(query).size.coerceAtMost(MAX_ITEMS), params)
        LibraryResult.ofVoid()
    }

    override fun onGetSearchResult(
        session: MediaLibrarySession, browser: MediaSession.ControllerInfo, query: String,
        page: Int, pageSize: Int, params: LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future {
        LibraryResult.ofItemList(catalog.search(query).take(MAX_ITEMS).map { bookItem(it.first) }, params)
    }

    /** Turns a picked book (or a spoken request like "play Sherlock Holmes") into its chapters, from your saved spot. */
    override fun onSetMediaItems(
        mediaSession: MediaSession, controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long,
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        val first = mediaItems.firstOrNull()
        val picked = first?.let { recordingIdOf(it.mediaId) }
        val spoken = first?.requestMetadata?.searchQuery
        // Items that already carry their audio (from the Voxshelf app itself) play as given.
        if (picked == null && spoken.isNullOrBlank()) {
            return super.onSetMediaItems(mediaSession, controller, mediaItems, startIndex, startPositionMs)
        }
        return scope.future {
            val id = picked ?: catalog.search(spoken!!).firstOrNull()?.first?.id ?: error("No book matches \"$spoken\"")
            val book = LibriVox.book(id, context.filesDir)
            context.getSharedPreferences("player", Context.MODE_PRIVATE).edit().putInt("id", id).apply()
            val saved = PositionStore(context).load(id)
            MediaSession.MediaItemsWithStartPosition(chapterItems(book, catalog.recording(id)?.cover), saved?.chapter ?: 0, saved?.positionMs ?: 0)
        }
    }

    private fun folder(id: String, title: String) = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_AUDIO_BOOKS)
                .setExtras(gridStyle)
                .build()
        )
        .build()

    private fun bookItem(r: Recording) = MediaItem.Builder()
        .setMediaId("$BOOK${r.id}")
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(r.title)
                .setArtist(r.authors)
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_AUDIO_BOOK)
                // Android Auto loads artwork through our own content provider, not from the web.
                .setArtworkUri(r.cover?.let { CarArtProvider.uriFor(it.id) })
                .build()
        )
        .build()

    private fun recordingIdOf(mediaId: String) = mediaId.removePrefix(BOOK).takeIf { mediaId.startsWith(BOOK) }?.toIntOrNull()

    private companion object {
        const val ROOT = "root"
        const val BOOK = "book:"
        const val MAX_ITEMS = 50
    }
}
