package app.voxshelf.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.voxshelf.Catalog
import app.voxshelf.Ratings
import app.voxshelf.Recording
import app.voxshelf.Sort
import app.voxshelf.Subject
import app.voxshelf.WorkSummary
import kotlinx.coroutines.delay

@Composable
fun BrowseScreen(
    catalog: Catalog,
    language: String?,
    onLanguage: (String?) -> Unit,
    query: String,
    onQuery: (String) -> Unit,
    onSubject: (shelf: String, subject: String) -> Unit,
    onBook: (WorkSummary) -> Unit,
) {
    var shelfIndex by rememberSaveable { mutableIntStateOf(0) }
    val shelf = Catalog.SHELVES[shelfIndex]
    var showLanguages by remember { mutableStateOf(false) }
    val subjects by produceState(emptyList<Subject>(), shelf, language) { value = catalog.subjects(shelf) }
    val everythingElse by produceState(0, language) { value = catalog.subjects("").sumOf { it.books } }
    val results by produceState<List<WorkSummary>?>(null, query, language) {
        value = if (query.isBlank()) null else {
            delay(200)
            catalog.search(query)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Voxshelf", style = text(30.sp, 800))
            Pill(language ?: "All languages") { showLanguages = true }
        }
        if (showLanguages) LanguageSheet(catalog, language, onDismiss = { showLanguages = false }) {
            onLanguage(it)
            showLanguages = false
        }
        SearchField(query, onQuery)
        Spacer(Modifier.height(16.dp))

        val found = results
        if (found != null) {
            Text(
                if (found.isEmpty()) "No books match \"$query\"" else "${found.size} books",
                style = text(14.sp, 700, Lantern.TextMuted),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            BookList(found, onBook)
            return@Column
        }

        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Catalog.SHELVES.forEachIndexed { i, name -> Pill(name, selected = i == shelfIndex) { shelfIndex = i } }
        }
        Spacer(Modifier.height(16.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(subjects) { subject ->
                Column(
                    Modifier
                        .clip(RoundedCornerShape(22.dp))
                        .background(Lantern.Surface)
                        .clickable(role = Role.Button) { onSubject(shelf, subject.name) }
                        .padding(16.dp)
                        .height(64.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(subject.name, style = text(16.sp, 800), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("%,d books".format(subject.books), style = text(13.sp, 600, Lantern.TextMuted))
                }
            }
            if (everythingElse > 0) item(span = { GridItemSpan(2) }) {
                Text(
                    "Everything else (%,d books without a category)".format(everythingElse),
                    style = text(14.sp, 700, Lantern.Gold),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSubject("", Catalog.EVERYTHING_ELSE) }
                        .padding(vertical = 14.dp, horizontal = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit) {
    Row(
        Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(Lantern.Surface)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.Canvas(Modifier.size(20.dp)) {
            val u = size.width / 24f
            drawCircle(Lantern.TextMuted, 7 * u, Offset(10.5f * u, 10.5f * u), style = Stroke(2.4f * u))
            drawLine(Lantern.TextMuted, Offset(16 * u, 16 * u), Offset(21 * u, 21 * u), 2.4f * u, StrokeCap.Round)
        }
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text("Search titles and authors", style = text(16.sp, 600, Lantern.TextMuted))
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = text(16.sp, 600),
                cursorBrush = SolidColor(Lantern.Gold),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Search titles and authors" },
            )
        }
        if (query.isNotEmpty()) {
            Text("Clear", style = text(14.sp, 700, Lantern.Gold), modifier = Modifier.clickable { onQuery("") }.padding(8.dp))
        }
    }
}

@Composable
fun SubjectScreen(catalog: Catalog, language: String?, shelf: String, subject: String, onBack: () -> Unit, onBook: (WorkSummary) -> Unit) {
    var sort by rememberSaveable { mutableStateOf(Sort.Popular) }
    val works by produceState<List<WorkSummary>?>(null, shelf, subject, sort, language) { value = catalog.works(shelf, subject, sort) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(end = 20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Header(onBack)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Sort.entries.forEach { option -> Pill(option.label, selected = option == sort) { sort = option } }
            }
        }
        Text(subject, style = text(28.sp, 800), modifier = Modifier.padding(horizontal = 20.dp))
        Text(
            listOfNotNull(shelf.ifBlank { null }, works?.let { "%,d books".format(it.size) }).joinToString(" · "),
            style = text(14.sp, 600, Lantern.TextMuted),
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 12.dp),
        )
        works?.let { BookList(it, onBook) }
    }
}

@Composable
private fun BookList(works: List<WorkSummary>, onBook: (WorkSummary) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        items(works, key = { it.first.workId }) { work ->
            val r = work.first
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onBook(work) }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Cover(r.title, r.authors, 64.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(r.title, style = text(16.sp, 800), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(r.authors.ifBlank { "Various authors" }, style = text(14.sp, 600, Lantern.TextSoft), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (work.recordings > 1) "${work.recordings} recordings" else listOfNotNull(length(r.seconds), readerLabel(r)).joinToString(" · "),
                        style = text(13.sp, 600, Lantern.TextMuted),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BookScreen(catalog: Catalog, library: app.voxshelf.Library, workId: Int, playingId: Int?, onBack: () -> Unit, onPlay: (Recording) -> Unit) {
    val recordings by produceState(emptyList<Recording>(), workId) { value = catalog.recordings(workId) }
    var selected by rememberSaveable(workId) { mutableIntStateOf(-1) }
    // Until you pick one, show the recording you're listening to (or the first).
    if (selected == -1 && recordings.isNotEmpty()) selected = recordings.indexOfFirst { it.id == playingId }.coerceAtLeast(0)
    val book = recordings.getOrNull(selected)
    val ratings = Ratings.get(androidx.compose.ui.platform.LocalContext.current)
    val summaries by produceState(emptyMap<Int, app.voxshelf.RatingSummary>(), recordings) {
        if (ratings.enabled) value = runCatching { ratings.summaries(recordings.map { it.id }) }.getOrDefault(emptyMap())
    }

    Column(Modifier.fillMaxSize()) {
        Header(onBack)
        if (book == null) return@Column
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            Cover(book.title, book.authors, 190.dp, glow = true)
            Spacer(Modifier.height(18.dp))
            Text(book.title, style = text(28.sp, 800), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Text(
                listOfNotNull(book.authors.ifBlank { null }, book.era).joinToString(" · "),
                style = text(15.sp, 400, Lantern.TextMuted),
            )
            if (book.mature) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Mature content",
                    style = text(13.sp, 800, Lantern.Forest),
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Lantern.Gold).padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }

            Spacer(Modifier.height(16.dp))
            LibraryActions(library, book)

            Spacer(Modifier.height(18.dp))
            Text(
                if (recordings.size > 1) "${recordings.size} recordings" else "Recording",
                style = text(14.sp, 800, Lantern.TextMuted),
                modifier = Modifier.fillMaxWidth().padding(start = 6.dp, bottom = 6.dp),
            )
            Column(
                Modifier.clip(RoundedCornerShape(22.dp)).background(Lantern.Surface).padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                recordings.forEachIndexed { i, r ->
                    val isSelected = i == selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (isSelected) Lantern.SurfaceHigh else Lantern.Surface)
                            .clickable(role = Role.RadioButton) { selected = i }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(r.readers.ifBlank { "LibriVox volunteers" }, style = text(15.sp, 700))
                            Text(
                                listOfNotNull(readerLabel(r), length(r.seconds), "${r.chapters} chapters").joinToString(" · "),
                                style = text(13.sp, 400, Lantern.TextMuted),
                            )
                            Text(ratingLine(summaries[r.id]), style = text(13.sp, 400, Lantern.TextMuted))
                            problemLine(summaries[r.id])?.let { Text(it, style = text(13.sp, 600, Lantern.Gold)) }
                        }
                        Box(
                            Modifier.size(22.dp).clip(CircleShape).background(if (isSelected) Lantern.Gold else Lantern.SurfaceHigh),
                        )
                    }
                }
            }

            if (book.description.isNotBlank()) {
                Spacer(Modifier.height(18.dp))
                Text(book.description, style = text(15.sp, 400, Lantern.TextSoft), lineHeight = 23.sp, modifier = Modifier.fillMaxWidth())
            }
            if (book.subjects.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    (listOf(book.shelf) + book.subjects).filter { it.isNotBlank() }.forEach {
                        Text(it, style = text(13.sp, 700, Lantern.TextSoft), modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(Lantern.Surface).padding(horizontal = 12.dp, vertical = 6.dp))
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
        Text(
            "Start listening",
            style = text(17.sp, 800, Lantern.Forest),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(Lantern.Gold)
                .clickable(role = Role.Button) { onPlay(book) }
                .padding(vertical = 16.dp),
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun LanguageSheet(catalog: Catalog, current: String?, onDismiss: () -> Unit, onPick: (String?) -> Unit) {
    val languages by produceState(emptyList<Subject>()) { value = catalog.languages() }
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Lantern.Surface) {
        Text("Show books in", style = text(20.sp, 800), modifier = Modifier.padding(start = 24.dp, bottom = 8.dp))
        LazyColumn(Modifier.padding(bottom = 24.dp)) {
            item { LanguageRow("All languages", null, current == null) { onPick(null) } }
            items(languages) { LanguageRow(it.name, it.books, it.name == current) { onPick(it.name) } }
        }
    }
}

@Composable
private fun LanguageRow(name: String, books: Int?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).background(if (selected) Lantern.SurfaceHigh else Lantern.Surface).padding(horizontal = 24.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(name, style = text(16.sp, if (selected) 800 else 600, if (selected) Lantern.Gold else Lantern.Text))
        if (books != null) Text("%,d".format(books), style = text(14.sp, 600, Lantern.TextMuted))
    }
}

@Composable
private fun Header(onBack: () -> Unit) {
    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        IconCircle("Back", onBack) { u -> chevron(u, down = false) }
    }
}

private fun readerLabel(r: Recording) = when {
    r.dramatic -> "Dramatic reading"
    r.solo -> "Solo reader"
    else -> "Group reading"
} + if (r.abridged) ", abridged" else ""

private fun length(seconds: Int) = if (seconds > 0) hoursMinutes(seconds * 1000L) else "Length unknown"
