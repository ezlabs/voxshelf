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
import app.voxshelf.Library
import app.voxshelf.UserList
import app.voxshelf.PlayerViewModel

private sealed interface Screen {
    data class Subject(val shelf: String, val name: String) : Screen
    data class Book(val workId: Int) : Screen
    data class Collection(val collection: app.voxshelf.Collection, val shuffle: Long = System.nanoTime()) : Screen
    data class Filtered(val filters: app.voxshelf.Filters) : Screen
    data object Player : Screen
    data class List(val list: UserList) : Screen
}

private enum class Tab(val label: String) { Library("Library"), Browse("Browse") }

@Composable
fun VoxshelfApp(player: PlayerViewModel = viewModel()) {
    val context = LocalContext.current
    val catalog = remember { Catalog.get(context) }
    val library = remember { Library.get(context) }
    var tab by rememberSaveable { mutableStateOf(Tab.Library) }
    val stack = remember { mutableStateListOf<Screen>() }
    var query by rememberSaveable { mutableStateOf("") }
    var filters by remember { mutableStateOf(app.voxshelf.Filters()) }
    var language by remember { mutableStateOf(catalog.language) }
    val playerState by player.state.collectAsStateWithLifecycle()

    // When a book ends, ask once how the recording was, wherever you are in the app.
    playerState.book?.let { book ->
        if (playerState.askToRate) RatingSheet(book.id, playerState.chapter, onDone = player::doneAskingToRate)
    }

    BackHandler(enabled = stack.isNotEmpty()) { stack.removeAt(stack.lastIndex) }

    Box(Modifier.fillMaxSize().background(Lantern.Forest)) {
        when (val screen = stack.lastOrNull()) {
            Screen.Player -> PlayerScreen(player, onClose = { stack.removeAt(stack.lastIndex) })
            else -> Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Box(Modifier.weight(1f)) {
                    when (screen) {
                        null -> if (tab == Tab.Library) LibraryScreen(
                            catalog, library,
                            onBook = { stack += Screen.Book(it) },
                            onList = { stack += Screen.List(it) },
                            onBrowse = { tab = Tab.Browse },
                        ) else BrowseScreen(
                            catalog, language, { catalog.language = it; language = it }, query, { query = it },
                            onSubject = { shelf, subject -> stack += Screen.Subject(shelf, subject) },
                            onCollection = { stack += Screen.Collection(it) },
                            filters = filters,
                            onFilters = { filters = it },
                            onFiltered = { stack += Screen.Filtered(it) },
                            onBook = { stack += Screen.Book(it.first.workId) },
                        )
                        is Screen.Subject -> SubjectScreen(
                            catalog, language, screen.shelf, screen.name,
                            onBack = { stack.removeAt(stack.lastIndex) },
                            onBook = { stack += Screen.Book(it.first.workId) },
                        )
                        is Screen.Collection -> androidx.compose.runtime.key(screen.shuffle) {
                            CollectionScreen(
                                catalog, language, screen.collection,
                                onBack = { stack.removeAt(stack.lastIndex) },
                                onBook = { stack += Screen.Book(it.first.workId) },
                            )
                        }
                        is Screen.Filtered -> FilteredScreen(
                            catalog, language, screen.filters,
                            onBack = { stack.removeAt(stack.lastIndex) },
                            onBook = { stack += Screen.Book(it.first.workId) },
                        )
                        is Screen.List -> UserListScreen(
                            catalog, library, screen.list,
                            onBack = { stack.removeAt(stack.lastIndex) },
                            onBook = { stack += Screen.Book(it) },
                        )
                        is Screen.Book -> BookScreen(
                            catalog, library, screen.workId, playerState.book?.id,
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
                        art = playerState.cover,
                        chapter = book.chapters.getOrNull(playerState.chapter)?.title.orEmpty(),
                        isPlaying = playerState.isPlaying,
                        onOpen = { stack += Screen.Player },
                        onPlayPause = player::playPause,
                    )
                }
                if (screen == null) TabBar(tab) { tab = it }
            }
        }
    }
}

@Composable
private fun TabBar(current: Tab, onSelect: (Tab) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Tab.entries.forEach { t ->
            val selected = t == current
            Text(
                t.label,
                style = text(15.sp, 800, if (selected) Lantern.Gold else Lantern.TextMuted),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (selected) Lantern.Surface else Lantern.Forest)
                    .clickable { onSelect(t) }
                    .padding(vertical = 12.dp),
            )
        }
    }
}

/** The "now playing" bar at the bottom of every screen, so you can always get back to your book. */
@Composable
private fun MiniPlayer(title: String, art: String?, chapter: String, isPlaying: Boolean, onOpen: () -> Unit, onPlayPause: () -> Unit) {
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
        Cover(title, "", 48.dp, art = art)
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
