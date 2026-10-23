package app.voxshelf.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import app.voxshelf.BookDownload
import app.voxshelf.Downloads
import app.voxshelf.LibriVox
import kotlinx.coroutines.delay
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.voxshelf.Catalog
import app.voxshelf.Library
import app.voxshelf.PositionStore
import app.voxshelf.Recording
import app.voxshelf.Status
import app.voxshelf.UserList
import kotlinx.coroutines.launch

private const val LISTS_TAB = "Lists"

@Composable
fun LibraryScreen(catalog: Catalog, library: Library, onBook: (workId: Int) -> Unit, onList: (UserList) -> Unit) {
    val tabs = Status.entries.map { it.label } + LISTS_TAB
    var tab by rememberSaveable { mutableStateOf(tabs.first()) }
    val changes by library.changes.collectAsState()
    var creating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        Text("Library", style = text(30.sp, 800), modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 12.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tabs.forEach { Pill(it, selected = it == tab) { tab = it } }
        }
        Spacer(Modifier.height(12.dp))

        if (tab == LISTS_TAB) {
            val lists by produceState(emptyList<UserList>(), changes) { value = library.lists() }
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                item {
                    Text(
                        "+ New list",
                        style = text(16.sp, 800, Lantern.Gold),
                        modifier = Modifier.fillMaxWidth().clickable { creating = true }.padding(horizontal = 20.dp, vertical = 16.dp),
                    )
                }
                items(lists, key = { it.id }) { list ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onList(list) }.padding(horizontal = 20.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(list.name, style = text(17.sp, 800), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (list.books == 1) "1 book" else "${list.books} books", style = text(14.sp, 600, Lantern.TextMuted))
                    }
                }
                if (lists.isEmpty()) item { Empty("Make lists for anything you like: a road trip, bedtime, books to share.") }
            }
        } else {
            val status = Status.entries.first { it.label == tab }
            val books by produceState<List<Recording>?>(null, status, changes) {
                value = library.entries(status).mapNotNull { catalog.recording(it.recordingId) }
            }
            books?.let { list ->
                if (list.isEmpty()) Empty(
                    when (status) {
                        Status.Listening -> "Books you start will show up here."
                        Status.Want -> "Tap \"Want to listen\" on any book to save it here."
                        Status.Finished -> "Books you finish will show up here."
                    }
                ) else BookRows(list, status, onBook)
            }
        }
    }

    if (creating) NameDialog("New list", "Create", onDismiss = { creating = false }) { name ->
        scope.launch { library.createList(name) }
        creating = false
    }
}

@Composable
private fun BookRows(books: List<Recording>, status: Status?, onBook: (workId: Int) -> Unit) {
    val positions = PositionStore(LocalContext.current)
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        items(books, key = { it.id }) { r ->
            Row(
                Modifier.fillMaxWidth().clickable { onBook(r.workId) }.padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Cover(r.title, r.authors, 64.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(r.title, style = text(16.sp, 800), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(r.authors.ifBlank { "Various authors" }, style = text(14.sp, 600, Lantern.TextSoft), maxLines = 1)
                    val saved = positions.load(r.id)
                    Text(
                        when {
                            status == Status.Listening && saved != null -> "Chapter ${saved.chapter + 1} of ${r.chapters}"
                            else -> r.readers
                        },
                        style = text(13.sp, 600, Lantern.TextMuted),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
fun UserListScreen(catalog: Catalog, library: Library, list: UserList, onBack: () -> Unit, onBook: (workId: Int) -> Unit) {
    val changes by library.changes.collectAsState()
    val books by produceState(emptyList<Recording>(), changes) { value = library.listItems(list.id).mapNotNull { catalog.recording(it) } }
    var name by remember { mutableStateOf(list.name) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(end = 20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) { IconCircle("Back", onBack) { u -> chevron(u, down = false) } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Rename") { renaming = true }
                Pill("Delete") { deleting = true }
            }
        }
        Text(name, style = text(28.sp, 800), modifier = Modifier.padding(horizontal = 20.dp))
        Text(if (books.size == 1) "1 book" else "${books.size} books", style = text(14.sp, 600, Lantern.TextMuted), modifier = Modifier.padding(start = 20.dp, bottom = 12.dp))
        if (books.isEmpty()) Empty("Add books to this list from any book's page.") else BookRows(books, null, onBook)
    }

    if (renaming) NameDialog("Rename list", "Save", initial = name, onDismiss = { renaming = false }) { newName ->
        scope.launch { library.renameList(list.id, newName) }
        name = newName
        renaming = false
    }
    if (deleting) AlertDialog(
        onDismissRequest = { deleting = false },
        containerColor = Lantern.Surface,
        title = { Text("Delete \"$name\"?", style = text(20.sp, 800)) },
        text = { Text("The books stay in your library. Only the list is removed.", style = text(15.sp, 400, Lantern.TextSoft)) },
        confirmButton = { DialogButton("Delete") { scope.launch { library.deleteList(list.id) }; deleting = false; onBack() } },
        dismissButton = { DialogButton("Cancel") { deleting = false } },
    )
}

/** Library actions on a book page: save for later, add to lists, mark finished. */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun LibraryActions(library: Library, book: Recording) {
    val changes by library.changes.collectAsState()
    val entry by produceState<app.voxshelf.LibraryEntry?>(null, book.workId, changes) { value = library.entry(book.workId) }
    var choosingLists by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val status = entry?.status

    androidx.compose.foundation.layout.FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (status) {
            null -> Pill("Want to listen") { scope.launch { library.setStatus(book.workId, book.id, Status.Want) } }
            Status.Want -> Pill("Saved ✓", selected = true) { scope.launch { library.setStatus(book.workId, book.id, null) } }
            Status.Listening -> Pill("Mark finished") { scope.launch { library.setStatus(book.workId, book.id, Status.Finished) } }
            Status.Finished -> Pill("Finished ✓", selected = true) { scope.launch { library.setStatus(book.workId, book.id, null) } }
        }
        Pill("Add to list") { choosingLists = true }
        DownloadPill(book)
    }

    if (choosingLists) {
        val lists by produceState(emptyList<UserList>(), changes) { value = library.lists() }
        val onLists by produceState(emptySet<Long>(), changes) { value = library.listsFor(book.workId) }
        var creating by remember { mutableStateOf(false) }
        ModalBottomSheet(onDismissRequest = { choosingLists = false }, containerColor = Lantern.Surface) {
            Text("Add to list", style = text(20.sp, 800), modifier = Modifier.padding(start = 24.dp, bottom = 8.dp))
            Column(Modifier.padding(bottom = 24.dp)) {
                lists.forEach { list ->
                    val on = list.id in onLists
                    Row(
                        Modifier.fillMaxWidth().clickable { scope.launch { library.setOnList(list.id, book.workId, book.id, !on) } }
                            .padding(horizontal = 24.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(list.name, style = text(16.sp, if (on) 800 else 600, if (on) Lantern.Gold else Lantern.Text))
                        Text(if (on) "Added ✓" else "Add", style = text(14.sp, 700, if (on) Lantern.Gold else Lantern.TextMuted))
                    }
                }
                Text(
                    "+ New list",
                    style = text(16.sp, 800, Lantern.Gold),
                    modifier = Modifier.fillMaxWidth().clickable { creating = true }.padding(horizontal = 24.dp, vertical = 14.dp),
                )
            }
        }
        if (creating) NameDialog("New list", "Create", onDismiss = { creating = false }) { name ->
            scope.launch {
                val id = library.createList(name)
                library.setOnList(id, book.workId, book.id, true)
            }
            creating = false
        }
    }
}

/** Save a book for offline listening, watch it download, or remove it again. */
@Composable
private fun DownloadPill(book: Recording) {
    val context = LocalContext.current
    val downloads = remember { Downloads.get(context) }
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<BookDownload>(BookDownload.None) }
    var starting by remember(book.id) { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    LaunchedEffect(book.id) {
        while (true) {
            status = downloads.status(book.id)
            if (status != BookDownload.None) starting = false
            delay(1_000)
        }
    }

    fun start() {
        starting = true
        scope.launch {
            try {
                downloads.download(LibriVox.book(book.id, context.filesDir))
            } catch (e: Exception) {
                starting = false
                error = true
            }
        }
    }

    when (val s = status) {
        BookDownload.None -> Pill(if (starting) "Downloading…" else "Download") { if (!starting) start() }
        is BookDownload.Downloading -> Pill("Downloading ${s.percent}%") { removing = true }
        BookDownload.Done -> Pill("Downloaded ✓", selected = true) { removing = true }
        BookDownload.Failed -> Pill("Download failed. Retry") { downloads.remove(book.id); start() }
    }

    val done = status == BookDownload.Done
    if (removing) AlertDialog(
        onDismissRequest = { removing = false },
        containerColor = Lantern.Surface,
        title = { Text(if (done) "Remove download?" else "Stop downloading?", style = text(20.sp, 800)) },
        text = { Text("You can still stream it, or download it again later.", style = text(15.sp, 400, Lantern.TextSoft)) },
        confirmButton = { DialogButton(if (done) "Remove" else "Stop") { downloads.remove(book.id); removing = false } },
        dismissButton = { DialogButton("Cancel") { removing = false } },
    )
    if (error) AlertDialog(
        onDismissRequest = { error = false },
        containerColor = Lantern.Surface,
        title = { Text("Couldn't start the download", style = text(20.sp, 800)) },
        text = { Text("Check your internet connection and try again.", style = text(15.sp, 400, Lantern.TextSoft)) },
        confirmButton = { DialogButton("OK") { error = false } },
    )
}

@Composable
private fun NameDialog(title: String, action: String, initial: String = "", onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Lantern.Surface,
        title = { Text(title, style = text(20.sp, 800)) },
        text = {
            BasicTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                textStyle = text(17.sp, 600),
                cursorBrush = SolidColor(Lantern.Gold),
                decorationBox = { field ->
                    androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Lantern.Forest).padding(horizontal = 16.dp, vertical = 14.dp)) {
                        if (name.isEmpty()) Text("List name", style = text(17.sp, 600, Lantern.TextMuted))
                        field()
                    }
                },
            )
        },
        confirmButton = { DialogButton(action, enabled = name.isNotBlank()) { onDone(name) } },
        dismissButton = { DialogButton("Cancel", onClick = onDismiss) },
    )
}

@Composable
private fun DialogButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        label,
        style = text(16.sp, 800, if (enabled) Lantern.Gold else Lantern.TextMuted),
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
    )
}

@Composable
private fun Empty(message: String) {
    Text(message, style = text(16.sp, 600, Lantern.TextMuted), modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp))
}
