package app.voxshelf

import android.app.Application
import android.content.ComponentName
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class SleepTimer(val label: String, val minutes: Int?) {
    Off("Off", null),
    Fifteen("15 minutes", 15),
    Thirty("30 minutes", 30),
    Sixty("1 hour", 60),
    EndOfChapter("End of chapter", null),
}

data class PlayerState(
    val book: Book? = null,
    /** The recording's LibriVox cover art (small version), if it has one. */
    val cover: String? = null,
    val error: String? = null,
    val isPlaying: Boolean = false,
    val chapter: Int = 0,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val speed: Float = 1f,
    val sleepTimer: SleepTimer = SleepTimer.Off,
    val sleepRemainingMs: Long? = null,
    /** Set when a book ends that you haven't rated yet, so the app can ask once. */
    val askToRate: Boolean = false,
) {
    val leftInBookMs: Long
        get() {
            val chapters = book?.chapters ?: return 0
            val later = chapters.drop(chapter + 1).sumOf { it.seconds } * 1000L
            return later + (durationMs - positionMs).coerceAtLeast(0)
        }
}

class PlayerViewModel(app: Application) : AndroidViewModel(app) {
    private val positions = PositionStore(app)
    private val catalog = Catalog.get(app)
    private val library = Library.get(app)
    private val ratings = Ratings.get(app)
    private val lastBook = app.getSharedPreferences("player", android.content.Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var controller: MediaController? = null
    private var sleepJob: Job? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refresh()

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            // Any time you press play, the book counts as one you're listening to.
            if (isPlaying) _state.value.book?.let { book ->
                viewModelScope.launch {
                    val workId = catalog.recording(book.id)?.workId ?: return@launch
                    if (library.entry(workId)?.status != Status.Listening) library.setStatus(workId, book.id, Status.Listening)
                }
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            // Reaching the end of the last chapter marks the book finished.
            if (playbackState == Player.STATE_ENDED) _state.value.book?.let { book ->
                viewModelScope.launch {
                    catalog.recording(book.id)?.let { library.setStatus(it.workId, book.id, Status.Finished) }
                }
                if (ratings.enabled && !ratings.asked(book.id)) _state.update { it.copy(askToRate = true) }
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && _state.value.sleepTimer == SleepTimer.EndOfChapter) {
                controller?.pause()
                setSleepTimer(SleepTimer.Off)
            }
        }
    }

    init {
        viewModelScope.launch {
            val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
            controller = MediaController.Builder(app, token).buildAsync().await().also { it.addListener(listener) }
            val playing = bookIdOf(controller?.currentMediaItem?.mediaId)
            val last = playing ?: lastBook.getInt("id", 0).takeIf { it != 0 }
            if (last != null) load(last, autoplay = false)
            while (isActive) {
                refresh()
                delay(500)
            }
        }
    }

    /** Opens a recording in the player and starts it from your saved spot. */
    fun open(bookId: Int) {
        viewModelScope.launch { load(bookId, autoplay = true) }
    }

    private suspend fun load(bookId: Int, autoplay: Boolean) {
        lastBook.edit().putInt("id", bookId).apply()
        if (_state.value.book?.id != bookId) _state.update { PlayerState() }
        val book = try {
            LibriVox.book(bookId, getApplication<Application>().filesDir)
        } catch (e: Exception) {
            _state.update { it.copy(error = "Couldn't load this book. Check your internet connection.") }
            return
        }
        val cover = catalog.recording(bookId)?.cover
        _state.update { it.copy(book = book, cover = cover, error = null) }
        val player = controller ?: return
        if (bookIdOf(player.currentMediaItem?.mediaId) == bookId) {
            if (autoplay && !player.isPlaying) playPause()
            return
        }

        val items = book.chapters.mapIndexed { i, chapter ->
            MediaItem.Builder()
                .setMediaId(mediaId(book.id, i))
                .setUri(chapter.url.toUri())
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(chapter.title)
                        .setArtist(book.author)
                        .setAlbumTitle(book.title)
                        .setArtworkUri(cover?.replace("_thumb", "")?.toUri())
                        .build()
                )
                .build()
        }
        val saved = positions.load(bookId)
        player.setMediaItems(items, saved?.chapter ?: 0, saved?.positionMs ?: 0)
        player.prepare()
        if (autoplay) player.play()
    }

    private fun refresh() {
        val p = controller ?: return
        _state.update {
            it.copy(
                isPlaying = p.isPlaying,
                chapter = p.currentMediaItemIndex,
                positionMs = p.currentPosition,
                durationMs = p.duration.takeIf { d -> d > 0 }
                    ?: ((it.book?.chapters?.getOrNull(p.currentMediaItemIndex)?.seconds ?: 0) * 1000L),
                speed = p.playbackParameters.speed,
            )
        }
    }

    fun playPause() {
        val p = controller ?: return
        if (p.isPlaying) {
            p.pause()
            return
        }
        // Smart rewind: back up a little after a long pause so you don't lose the thread.
        val bookId = _state.value.book?.id
        val pausedFor = bookId?.let { positions.load(it) }?.let { System.currentTimeMillis() - it.pausedAt } ?: 0
        val rewindMs = when {
            pausedFor > 60 * 60_000 -> 15_000L
            pausedFor > 60_000 -> 5_000L
            else -> 0L
        }
        if (rewindMs > 0) p.seekTo((p.currentPosition - rewindMs).coerceAtLeast(0))
        p.play()
    }

    /** Closes the end-of-book question; it won't come back for this recording. */
    fun doneAskingToRate() {
        _state.value.book?.let { ratings.markAsked(it.id) }
        _state.update { it.copy(askToRate = false) }
    }

    fun back() = controller?.seekBack()
    fun forward() = controller?.seekForward()
    fun seekTo(fraction: Float) = controller?.let { it.seekTo((it.duration * fraction).toLong()) }
    fun playChapter(index: Int) = controller?.run { seekTo(index, 0); play() }

    fun cycleSpeed() {
        val p = controller ?: return
        val next = SPEEDS.firstOrNull { it > p.playbackParameters.speed + 0.01f } ?: SPEEDS.first()
        p.setPlaybackSpeed(next)
    }

    fun setSleepTimer(timer: SleepTimer) {
        sleepJob?.cancel()
        _state.update { it.copy(sleepTimer = timer, sleepRemainingMs = timer.minutes?.let { m -> m * 60_000L }) }
        val minutes = timer.minutes ?: return
        sleepJob = viewModelScope.launch {
            val endAt = System.currentTimeMillis() + minutes * 60_000L
            while (isActive) {
                val left = endAt - System.currentTimeMillis()
                if (left <= 0) {
                    controller?.pause()
                    _state.update { it.copy(sleepTimer = SleepTimer.Off, sleepRemainingMs = null) }
                    break
                }
                _state.update { it.copy(sleepRemainingMs = left) }
                delay(1_000)
            }
        }
    }

    override fun onCleared() {
        controller?.removeListener(listener)
        controller?.release()
    }

    private companion object {
        val SPEEDS = listOf(0.8f, 1f, 1.1f, 1.2f, 1.35f, 1.5f, 1.75f, 2f)
    }
}
