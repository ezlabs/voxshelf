package app.voxshelf.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.voxshelf.Catalog
import app.voxshelf.PlayerViewModel

private sealed interface Screen {
    data class Subject(val shelf: String, val name: String) : Screen
    data class Book(val workId: Int) : Screen
    data object Player : Screen
}

@Composable
fun VoxshelfApp(player: PlayerViewModel = viewModel()) {
    val context = LocalContext.current
    val catalog = remember { Catalog(context.applicationContext) }
    val stack = remember { mutableStateListOf<Screen>() }
    var query by rememberSaveable { mutableStateOf("") }
    val playerState by player.state.collectAsStateWithLifecycle()

    BackHandler(enabled = stack.isNotEmpty()) { stack.removeAt(stack.lastIndex) }

    Box(Modifier.fillMaxSize().background(Lantern.Forest)) {
        when (val screen = stack.lastOrNull()) {
            Screen.Player -> PlayerScreen(player, onClose = { stack.removeAt(stack.lastIndex) })
            else -> Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Box(Modifier.weight(1f)) {
                    when (screen) {
                        null -> BrowseScreen(
                            catalog, query, { query = it },
                            onSubject = { shelf, subject -> stack += Screen.Subject(shelf, subject) },
                            onBook = { stack += Screen.Book(it.first.workId) },
                        )
                        is Screen.Subject -> SubjectScreen(
                            catalog, screen.shelf, screen.name,
                            onBack = { stack.removeAt(stack.lastIndex) },
                            onBook = { stack += Screen.Book(it.first.workId) },
                        )
                        is Screen.Book -> BookScreen(
                            catalog, screen.workId, playerState.book?.id,
                            onBack = { stack.removeAt(stack.lastIndex) },
                            onPlay = {
                                player.open(it.id)
                                stack += Screen.Player
                            },
                        )
                        Screen.Player -> Unit
                    }
                }
                val book = playerState.book
                if (book != null) {
                    MiniPlayer(
                        title = book.title,
                        chapter = book.chapters.getOrNull(playerState.chapter)?.title.orEmpty(),
                        isPlaying = playerState.isPlaying,
                        onOpen = { stack += Screen.Player },
                        onPlayPause = player::playPause,
                    )
                }
            }
        }
    }
}

/** The "now playing" bar at the bottom of every screen, so you can always get back to your book. */
@Composable
private fun MiniPlayer(title: String, chapter: String, isPlaying: Boolean, onOpen: () -> Unit, onPlayPause: () -> Unit) {
    Row(
        Modifier
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Lantern.SurfaceHigh)
            .clickable(onClick = onOpen)
            .semantics { contentDescription = "Open player" }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(title, "", 48.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = text(15.sp, 800), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(chapter, style = text(13.sp, 600, Lantern.TextMuted), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Lantern.Gold)
                .clickable(onClick = onPlayPause)
                .semantics { contentDescription = if (isPlaying) "Pause" else "Play" },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(20.dp)) {
                val w = size.width
                if (isPlaying) {
                    drawRoundRect(Lantern.Forest, Offset(w * 0.22f, w * 0.15f), Size(w * 0.2f, w * 0.7f), CornerRadius(w * 0.08f))
                    drawRoundRect(Lantern.Forest, Offset(w * 0.58f, w * 0.15f), Size(w * 0.2f, w * 0.7f), CornerRadius(w * 0.08f))
                } else {
                    drawPath(Path().apply {
                        moveTo(w * 0.25f, w * 0.12f); lineTo(w * 0.88f, w * 0.5f); lineTo(w * 0.25f, w * 0.88f); close()
                    }, Lantern.Forest)
                }
            }
        }
    }
}
