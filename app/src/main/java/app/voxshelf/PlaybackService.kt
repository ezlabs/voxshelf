package app.voxshelf

import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Plays audio in the background and powers the lock screen, notification,
 * headphone and Bluetooth controls. Saves the listening spot as it goes.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
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

    override fun onCreate() {
        super.onCreate()
        positions = PositionStore(this)
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
                }
            }

            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                savePosition(player)
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && Sleep.setting.value.timer == SleepTimer.EndOfChapter) {
                    player.pause()
                    Sleep.set(SleepTimer.Off)
                }
            }
        })
        session = MediaSession.Builder(this, player).build()
    }

    private fun savePosition(player: Player) {
        val bookId = bookIdOf(player.currentMediaItem?.mediaId) ?: return
        positions.save(bookId, player.currentMediaItemIndex, player.currentPosition)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(saveTick)
        handler.removeCallbacks(sleepTick)
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
