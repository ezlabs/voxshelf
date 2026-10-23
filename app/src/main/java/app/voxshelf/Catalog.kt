package app.voxshelf

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** One recording of a book, as stored in the on-phone catalog. */
data class Recording(
    val id: Int,
    val workId: Int,
    val title: String,
    val authors: String,
    val description: String,
    val seconds: Int,
    val chapters: Int,
    val readers: String,
    val solo: Boolean,
    val dramatic: Boolean,
    val abridged: Boolean,
    val mature: Boolean,
    val era: String?,
    val shelf: String,
    val subjects: List<String>,
    val language: String,
    val downloads: Int,
    /** LibriVox cover art (small version), or null if the recording has none. */
    val cover: String?,
    /** Best guess at the year the book was written (shared by all its recordings), if known. */
    val year: Int?,
    /** The year LibriVox released this recording, if known. */
    val recorded: Int?,
)

/** A book in a list: its first recording stands in for the rest. */
enum class Sort(val label: String, val sql: String) {
    Popular("Popular", "work_downloads DESC"),
    Newest("Newest", "id DESC"),
    Title("A to Z", "title COLLATE NOCASE"),
}

data class WorkSummary(val first: Recording, val recordings: Int)

data class Subject(val name: String, val books: Int)

/**
 * The whole LibriVox catalog, kept on the phone so browsing and search are instant and work offline.
 * The app ships with a copy; nightly updates will replace it later.
 */
class Catalog(private val context: Context) {
    private val db: SQLiteDatabase by lazy { open() }

    private fun open(): SQLiteDatabase {
        val file = File(context.filesDir, "catalog.db")
        // Copy the bundled catalog on first launch and whenever the app itself is updated.
        val installedAt = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
        val prefs = context.getSharedPreferences("catalog", Context.MODE_PRIVATE)
        if (!file.exists() || prefs.getLong("copiedFor", -1) != installedAt) {
            context.assets.open("catalog.db").use { input -> file.outputStream().use { input.copyTo(it) } }
            prefs.edit().putLong("copiedFor", installedAt).apply()
        }
        return SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
    }

    /** The language filter: null shows every language. Defaults to the phone's language. */
    var language: String? = prefs.getString("language", null) ?: deviceLanguage()
        set(value) {
            field = value
            prefs.edit().putString("language", value ?: ALL_LANGUAGES).apply()
        }
        get() = field?.takeIf { it != ALL_LANGUAGES }

    private val prefs get() = context.getSharedPreferences("catalog", Context.MODE_PRIVATE)

    private fun deviceLanguage() = java.util.Locale.getDefault().getDisplayLanguage(java.util.Locale.ENGLISH)

    private val langFilter get() = if (language == null) "1" else "language = ?"
    private fun langArgs() = listOfNotNull(language)

    suspend fun languages(): List<Subject> =
        query("SELECT language, COUNT(DISTINCT work_id) n FROM book GROUP BY language ORDER BY n DESC") {
            buildList { while (it.moveToNext()) add(Subject(it.getString(0) ?: "", it.getInt(1))) }
        }

    suspend fun subjects(shelf: String): List<Subject> = query("SELECT subjects, work_id FROM book WHERE shelf = ? AND $langFilter", shelf, *langArgs().toTypedArray()) {
        val works = mutableMapOf<String, MutableSet<Int>>()
        while (it.moveToNext()) {
            val workId = it.getInt(1)
            it.getString(0).split('|').filter(String::isNotBlank).forEach { s -> works.getOrPut(s) { mutableSetOf() } += workId }
        }
        works.map { (name, ids) -> Subject(name, ids.size) }.sortedByDescending { s -> s.books }
    }

    suspend fun works(shelf: String, subject: String, sort: Sort): List<WorkSummary> = query(
        "SELECT * FROM book WHERE shelf = ? AND ('|' || subjects || '|') LIKE ? AND $langFilter ORDER BY ${sort.sql}, downloads DESC, id",
        shelf, "%|$subject|%", *langArgs().toTypedArray(),
    ) { group(it) }

    suspend fun search(text: String): List<WorkSummary> {
        val terms = text.split(Regex("\\W+")).filter { it.isNotBlank() }
        if (terms.isEmpty()) return emptyList()
        val match = terms.joinToString(" ") { "$it*" }
        return query(
            "SELECT b.* FROM book_fts f JOIN book b ON b.id = f.docid WHERE book_fts MATCH ? AND ${langFilter.replace("language", "b.language")} " +
                "ORDER BY b.work_downloads DESC, b.downloads DESC, b.id LIMIT 300",
            match, *langArgs().toTypedArray(),
        ) { group(it) }
    }

    suspend fun recordings(workId: Int): List<Recording> =
        query("SELECT * FROM book WHERE work_id = CAST(? AS INTEGER) ORDER BY downloads DESC, id", workId.toString()) { c ->
            buildList { while (c.moveToNext()) add(c.toRecording()) }
        }

    suspend fun recording(id: Int): Recording? =
        query("SELECT * FROM book WHERE id = CAST(? AS INTEGER)", id.toString()) { c -> if (c.moveToNext()) c.toRecording() else null }

    private fun group(c: Cursor): List<WorkSummary> {
        val byWork = LinkedHashMap<Int, MutableList<Recording>>()
        while (c.moveToNext()) {
            val r = c.toRecording()
            byWork.getOrPut(r.workId) { mutableListOf() } += r
        }
        return byWork.values.map { WorkSummary(it.first(), it.size) }
    }

    private suspend fun <T> query(sql: String, vararg args: String, read: (Cursor) -> T): T =
        withContext(Dispatchers.IO) { db.rawQuery(sql, args).use(read) }

    private fun Cursor.toRecording(): Recording {
        fun str(col: String) = getString(getColumnIndexOrThrow(col)) ?: ""
        fun int(col: String) = getInt(getColumnIndexOrThrow(col))
        return Recording(
            id = int("id"),
            workId = int("work_id"),
            title = str("title"),
            authors = str("authors"),
            description = str("description"),
            seconds = int("secs"),
            chapters = int("chapters"),
            readers = str("readers"),
            solo = int("solo") == 1,
            dramatic = int("dramatic") == 1,
            abridged = int("abridged") == 1,
            mature = int("mature") == 1,
            era = str("era").ifBlank { null },
            shelf = str("shelf"),
            subjects = str("subjects").split('|').filter(String::isNotBlank),
            language = str("language"),
            downloads = int("downloads"),
            cover = str("cover").ifBlank { null },
            year = int("year").takeIf { it > 0 },
            recorded = int("recorded").takeIf { it > 0 },
        )
    }

    companion object {
        @Volatile private var instance: Catalog? = null
        fun get(context: Context) = instance ?: synchronized(this) {
            instance ?: Catalog(context.applicationContext).also { instance = it }
        }

        val SHELVES = listOf("Fiction", "Nonfiction", "Poetry & Plays", "Kids")
        private const val ALL_LANGUAGES = "*"
        const val EVERYTHING_ELSE = "Everything else"
    }
}
