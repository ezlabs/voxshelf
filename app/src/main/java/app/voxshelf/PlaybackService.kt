package app.voxshelf

import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaLibraryService
import kotlinx.coroutines.launch
import androidx.media3.session.MediaLibraryService.MediaLibrarySession

/**
 * Plays audio in the background and powers the lock screen, notification, headphone, Bluetooth and
 * Android Auto controls. Saves the listening spot and updates the shelf as it goes, even with the app closed.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackService : MediaLibraryService() {
    private var session: MediaLibrarySession? = null
    private lateinit var positions: PositionStore
    private val handler = Handler(Looper.getMainLooper())

    // Checks the sleep timer every second while playing; the service runs even when the app's screen is closed.
    private val sleepTick = object : Runnable {
        override fun run() {
            val end = Sleep.setting.value.endAt
            if (end != null && System.currentTimeMillis() >= end) {
                session?.player?.pause()
                Sleep.set(SleepTimer.Off)
            }
            handler.postDelayed(this, 1_000)
        }
    }

    private val saveTick = object : Runnable {
        override fun run() {
            session?.player?.let { savePosition(it) }
            handler.postDelayed(this, SAVE_EVERY_MS)
        }
    }

    private lateinit var ahead: ListenAhead

    override fun onCreate() {
        super.onCreate()
        positions = PositionStore(this)
        ahead = ListenAhead(this)
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(Downloads.get(this).playerSource))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setSeekBackIncrementMs(15_000)
            .setSeekForwardIncrementMs(30_000)
            .build()
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                savePosition(player)
                handler.removeCallbacks(saveTick)
                handler.removeCallbacks(sleepTick)
                if (isPlaying) {
                    // A timer that ran out while paused is stale: drop it rather than pausing again right away.
                    Sleep.setting.value.endAt?.let { if (System.currentTimeMillis() >= it) Sleep.set(SleepTimer.Off) }
                    handler.postDelayed(saveTick, SAVE_EVERY_MS)
                    handler.post(sleepTick)
                    ahead.update(player)
                    updateShelf(player, Status.Listening)
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    ahead.clear(player)
                    updateShelf(player, Status.Finished)
                }
            }

            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                savePosition(player)
                if (player.isPlaying) ahead.update(player)
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && Sleep.setting.value.timer == SleepTimer.EndOfChapter) {
                    player.pause()
                    Sleep.set(SleepTimer.Off)
                }
            }
        })
        // Back 15 / forward 30 seconds instead of previous / next chapter, in the car, notification and lock screen.
        // Chapters stay reachable from the car's queue and the app's chapter list.
        val buttons = listOf(
            androidx.media3.session.CommandButton.Builder(androidx.media3.session.CommandButton.ICON_SKIP_BACK_15)
                .setPlayerCommand(Player.COMMAND_SEEK_BACK)
                .setDisplayName("Back 15 seconds")
                .setSlots(androidx.media3.session.CommandButton.SLOT_BACK)
                .build(),
            androidx.media3.session.CommandButton.Builder(androidx.media3.session.CommandButton.ICON_SKIP_FORWARD_30)
                .setPlayerCommand(Player.COMMAND_SEEK_FORWARD)
                .setDisplayName("Forward 30 seconds")
                .setSlots(androidx.media3.session.CommandButton.SLOT_FORWARD)
                .build(),
        )
        session = MediaLibrarySession.Builder(this, player, CarLibrary(this))
            .setMediaButtonPreferences(buttons)
            .build()
    }

    /** Pressing play puts a book on the Listening shelf; reaching the end marks it finished (also when playing from the car). */
    private fun updateShelf(player: Player, status: Status) {
        val bookId = bookIdOf(player.currentMediaItem?.mediaId) ?: return
        scope.launch {
            val workId = Catalog.get(this@PlaybackService).recording(bookId)?.workId ?: return@launch
            val library = Library.get(this@PlaybackService)
            if (library.entry(workId)?.status != status) library.setStatus(workId, bookId, status)
        }
    }

    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)

    private fun savePosition(player: Player) {
        val bookId = bookIdOf(player.currentMediaItem?.mediaId) ?: return
        positions.save(bookId, player.currentMediaItemIndex, player.currentPosition)
    }

    override fun onGetSession(controllerInfo: androidx.media3.session.MediaSession.ControllerInfo) = session

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(saveTick)
        handler.removeCallbacks(sleepTick)
        ahead.release()
        session?.run {
            savePosition(player)
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    private companion object {
        const val SAVE_EVERY_MS = 5_000L
    }
}
