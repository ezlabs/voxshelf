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
import androidx.compose.foundation.lazy.LazyRow
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
import app.voxshelf.Collection
import app.voxshelf.Filters
import app.voxshelf.Length
import app.voxshelf.Reader
import app.voxshelf.Written
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
    onCollection: (Collection) -> Unit,
    filters: Filters,
    onFilters: (Filters) -> Unit,
    onFiltered: (Filters) -> Unit,
    onBook: (WorkSummary) -> Unit,
) {
    var showLanguages by remember { mutableStateOf(false) }
    var showFilter by remember { mutableStateOf(false) }
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
            val gallery = rememberGallery()
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (found.isEmpty()) "No books match \"$query\"" else "${found.size} books",
                    style = text(14.sp, 700, Lantern.TextMuted),
                    modifier = Modifier.weight(1f),
                )
                if (found.isNotEmpty()) ViewToggle(gallery)
            }
            BookList(found, gallery.value, onBook)
            return@Column
        }

        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Sliders icon: three lines, each with a knob.
            IconCircle("Filters", { showFilter = true }) { u ->
                listOf(6f to 15f, 12f to 8f, 18f to 13f).forEach { (y, knob) ->
                    drawLine(Lantern.Text, Offset(3 * u, y * u), Offset(21 * u, y * u), 2f * u, StrokeCap.Round)
                    drawCircle(Lantern.Surface, 3.2f * u, Offset(knob * u, y * u))
                    drawCircle(Lantern.Text, 2.6f * u, Offset(knob * u, y * u), style = Stroke(2f * u))
                }
            }
            Collection.entries.forEach { c -> Pill(c.label) { onCollection(c) } }
        }
        if (showFilter) FilterSheet(catalog, language, filters, onFilters, onDismiss = { showFilter = false }) {
            showFilter = false
            onFiltered(filters)
        }
        Spacer(Modifier.height(8.dp))
        val rows by produceState(emptyList<Pair<String, Subject>>(), language) { value = catalog.topSubjects(10) }
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item(key = "popular") {
                ShelfRow(Collection.Popular.title, { catalog.collection(Collection.Popular, 15) }, language, onBook) { onCollection(Collection.Popular) }
            }
            items(rows, key = { "${it.first}/${it.second.name}" }) { (shelf, subject) ->
                ShelfRow(subject.name, { catalog.works(shelf, subject.name, Sort.Recommended, 60).take(15) }, language, onBook) { onSubject(shelf, subject.name) }
            }
        }
    }
}

/** One horizontally scrolling row of covers, with "See all" for the full list. */
@Composable
private fun ShelfRow(title: String, load: suspend () -> List<WorkSummary>, language: String?, onBook: (WorkSummary) -> Unit, onSeeAll: () -> Unit) {
    val works by produceState(emptyList<WorkSummary>(), title, language) { value = load() }
    Column(Modifier.padding(top = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = text(19.sp, 800), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(
                "See all ›",
                style = text(14.sp, 800, Lantern.Gold),
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onSeeAll).padding(12.dp),
            )
        }
        val rowState = androidx.compose.foundation.lazy.rememberLazyListState()
        PrefetchCovers(works) { rowState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.height(232.dp),
        ) {
            items(works, key = { it.first.workId }) { work ->
                val r = work.first
                Column(Modifier.width(130.dp).clip(RoundedCornerShape(8.dp)).clickable { onBook(work) }) {
                    Cover(r.title, r.authors, 130.dp, art = r.cover)
                    Spacer(Modifier.height(8.dp))
                    Text(r.title, style = text(14.sp, 800), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    AuthorLine(r, 12.sp)
                }
            }
        }
    }
}

/** The Filter sheet: pick any mix of subject, length, reader and when it was written, then see the books. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(catalog: Catalog, language: String?, filters: Filters, onChange: (Filters) -> Unit, onDismiss: () -> Unit, onShow: () -> Unit) {
    val shelf = filters.shelf ?: Catalog.SHELVES[0]
    val subjects by produceState(emptyList<Subject>(), shelf, language) { value = catalog.subjects(shelf) }
    val count by produceState<Int?>(null, filters, language) { value = catalog.count(filters) }
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Lantern.Forest,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            FilterTitle("Subject")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Catalog.SHELVES.forEach { name ->
                    Pill(name, selected = filters.shelf == name) {
                        onChange(filters.copy(shelf = if (filters.shelf == name) null else name, subject = null))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                subjects.forEach { subject ->
                    Pill(subject.name, selected = filters.subject == subject.name) {
                        onChange(filters.copy(shelf = shelf, subject = if (filters.subject == subject.name) null else subject.name))
                    }
                }
            }
            FilterTitle("Length")
            Choices(Length.entries, filters.length, { it.label }) { onChange(filters.copy(length = it)) }
            FilterTitle("Reader")
            Choices(Reader.entries, filters.reader, { it.label }) { onChange(filters.copy(reader = it)) }
            FilterTitle("When it was written")
            Choices(Written.entries, filters.written, { it.label }) { onChange(filters.copy(written = it)) }
            Spacer(Modifier.height(12.dp))
            Toggle("Hide abridged books", filters.hideAbridged) { onChange(filters.copy(hideAbridged = it)) }
            Toggle("Hide mature content", filters.hideMature) { onChange(filters.copy(hideMature = it)) }
            Spacer(Modifier.height(12.dp))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Clear",
                style = text(16.sp, 800, if (filters.isEmpty) Lantern.TextMuted else Lantern.Gold),
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(enabled = !filters.isEmpty) { onChange(Filters()) }.padding(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            val n = count
            Text(
                when (n) { null -> "Show books"; 0 -> "No books match"; 1 -> "Show 1 book"; else -> "Show %,d books".format(n) },
                style = text(17.sp, 800, if (n != 0) Lantern.Forest else Lantern.TextMuted),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(28.dp))
                    .background(if (n != 0) Lantern.Gold else Lantern.SurfaceHigh)
                    .clickable(enabled = n != 0, role = Role.Button, onClick = onShow)
                    .padding(vertical = 16.dp),
            )
        }
    }
}

@Composable
private fun FilterTitle(title: String) {
    Text(title, style = text(17.sp, 800), modifier = Modifier.padding(top = 20.dp, bottom = 10.dp))
}

/** Pick one or none: tapping the chosen option again clears it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> Choices(options: List<T>, chosen: T?, label: (T) -> String, onChoose: (T?) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o -> Pill(label(o), selected = o == chosen) { onChoose(if (o == chosen) null else o) } }
    }
}

@Composable
private fun Toggle(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(role = Role.Switch) { onChange(!on) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = text(16.sp, 600), modifier = Modifier.weight(1f))
        androidx.compose.material3.Switch(
            checked = on,
            onCheckedChange = null,
            colors = androidx.compose.material3.SwitchDefaults.colors(
                checkedThumbColor = Lantern.Forest, checkedTrackColor = Lantern.Gold,
                uncheckedThumbColor = Lantern.TextMuted, uncheckedTrackColor = Lantern.Surface, uncheckedBorderColor = Lantern.SurfaceHigh,
            ),
        )
    }
}

/** The books matching the Filter sheet. */
@Composable
fun FilteredScreen(catalog: Catalog, language: String?, filters: Filters, onBack: () -> Unit, onBook: (WorkSummary) -> Unit) {
    val works by produceState<List<WorkSummary>?>(null, filters, language) { value = catalog.filtered(filters) }
    Column(Modifier.fillMaxSize()) {
        Header(onBack)
        Text(filters.subject ?: filters.shelf ?: "Filtered books", style = text(28.sp, 800), modifier = Modifier.padding(horizontal = 20.dp))
        val gallery = rememberGallery()
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                (listOfNotNull(filters.length?.label, filters.reader?.label, filters.written?.label) + listOfNotNull(works?.let { "%,d books".format(it.size) })).joinToString(" · "),
                style = text(14.sp, 600, Lantern.TextMuted),
                modifier = Modifier.weight(1f),
            )
            ViewToggle(gallery)
        }
        works?.let { BookList(it, gallery.value, onBook) }
    }
}

/** The full list behind a Popular / New / Surprise me pill. */
@Composable
fun CollectionScreen(catalog: Catalog, language: String?, collection: Collection, onBack: () -> Unit, onBook: (WorkSummary) -> Unit) {
    val works by produceState<List<WorkSummary>?>(null, collection, language) { value = catalog.collection(collection) }
    Column(Modifier.fillMaxSize()) {
        Header(onBack)
        Text(collection.title, style = text(28.sp, 800), modifier = Modifier.padding(horizontal = 20.dp))
        val gallery = rememberGallery()
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(works?.let { "%,d books".format(it.size) }.orEmpty(), style = text(14.sp, 600, Lantern.TextMuted), modifier = Modifier.weight(1f))
            ViewToggle(gallery)
        }
        works?.let { BookList(it, gallery.value, onBook) }
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
    var sort by rememberSaveable { mutableStateOf(Sort.Recommended) }
    val works by produceState<List<WorkSummary>?>(null, shelf, subject, sort, language) { value = catalog.works(shelf, subject, sort) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(end = 20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Header(onBack)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Sort.entries.forEach { option -> Pill(option.label, selected = option == sort) { sort = option } }
            }
        }
        Text(subject, style = text(28.sp, 800), modifier = Modifier.padding(horizontal = 20.dp))
        val gallery = rememberGallery()
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                listOfNotNull(shelf.ifBlank { null }, works?.let { "%,d books".format(it.size) }).joinToString(" · "),
                style = text(14.sp, 600, Lantern.TextMuted),
                modifier = Modifier.weight(1f),
            )
            ViewToggle(gallery)
        }
        works?.let { BookList(it, gallery.value, onBook) }
    }
}

/** Whether book lists show as a gallery of covers. Remembered between visits, shared by search and subjects. */
@Composable
private fun rememberGallery(): androidx.compose.runtime.MutableState<Boolean> {
    val prefs = androidx.compose.ui.platform.LocalContext.current.getSharedPreferences("browse", android.content.Context.MODE_PRIVATE)
    val state = remember { mutableStateOf(prefs.getBoolean("gallery", false)) }
    androidx.compose.runtime.LaunchedEffect(state.value) { prefs.edit().putBoolean("gallery", state.value).apply() }
    return state
}

@Composable
private fun ViewToggle(gallery: androidx.compose.runtime.MutableState<Boolean>) {
    Row(Modifier.clip(RoundedCornerShape(18.dp)).background(Lantern.Surface).padding(3.dp)) {
        listOf(false to "List", true to "Gallery").forEach { (isGallery, label) ->
            val on = gallery.value == isGallery
            Text(
                label,
                style = text(13.sp, 800, if (on) Lantern.Forest else Lantern.TextSoft),
                modifier = Modifier
                    .clip(RoundedCornerShape(15.dp))
                    .background(if (on) Lantern.Gold else Lantern.Surface)
                    .clickable(role = Role.Tab) { gallery.value = isGallery }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun BookList(works: List<WorkSummary>, gallery: Boolean, onBook: (WorkSummary) -> Unit) {
    if (gallery) {
        val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
        PrefetchCovers(works) { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            items(works, key = { it.first.workId }) { work ->
                val r = work.first
                Column(Modifier.clickable { onBook(work) }) {
                    androidx.compose.foundation.layout.BoxWithConstraints { Cover(r.title, r.authors, maxWidth, art = r.cover) }
                    Spacer(Modifier.height(8.dp))
                    Text(r.title, style = text(15.sp, 800), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    AuthorLine(r, 13.sp)
                }
            }
        }
        return
    }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    PrefetchCovers(works) { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
    LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
        items(works, key = { it.first.workId }) { work ->
            val r = work.first
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onBook(work) }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Cover(r.title, r.authors, 100.dp, art = r.cover)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(r.title, style = text(16.sp, 800), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    AuthorLine(r, 14.sp)
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
            Cover(book.title, book.authors, 190.dp, art = book.cover, glow = true)
            Spacer(Modifier.height(18.dp))
            Text(book.title, style = text(28.sp, 800), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Text(
                listOfNotNull(book.authors.ifBlank { null }, book.year?.toString() ?: book.era).joinToString(" · "),
                style = text(15.sp, 400, Lantern.TextMuted),
            )
            if (book.olRating != null && book.olCount != null) {
                Text(
                    "★ %.1f · %,d ratings on Open Library".format(book.olRating, book.olCount),
                    style = text(14.sp, 700, Lantern.Gold),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
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
                                listOfNotNull(readerLabel(r), length(r.seconds), "${r.chapters} chapters", r.recorded?.let { "Recorded $it" }).joinToString(" · "),
                                style = text(13.sp, 400, Lantern.TextMuted),
                            )
                            Text(listOf(downloadsLabel(r.downloads), ratingLine(summaries[r.id])).joinToString(" · "), style = text(13.sp, 400, Lantern.TextMuted))
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

/** "48k downloads", rounded so the number is easy to compare at a glance. */
private fun downloadsLabel(n: Int) = when {
    n >= 1_000_000 -> "%.1fM downloads".format(n / 1_000_000.0).replace(".0M", "M")
    n >= 1_000 -> "${n / 1_000}k downloads"
    n == 1 -> "1 download"
    else -> "$n downloads"
}

private fun length(seconds: Int) = if (seconds > 0) hoursMinutes(seconds * 1000L) else "Length unknown"
