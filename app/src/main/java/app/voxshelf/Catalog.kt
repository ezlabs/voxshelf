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
)

/** A book in a list: its first recording stands in for the rest. */
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

    suspend fun subjects(shelf: String): List<Subject> = query("SELECT subjects, work_id FROM book WHERE shelf = ?", shelf) {
        val works = mutableMapOf<String, MutableSet<Int>>()
        while (it.moveToNext()) {
            val workId = it.getInt(1)
            it.getString(0).split('|').filter(String::isNotBlank).forEach { s -> works.getOrPut(s) { mutableSetOf() } += workId }
        }
        works.map { (name, ids) -> Subject(name, ids.size) }.sortedByDescending { s -> s.books }
    }

    suspend fun works(shelf: String, subject: String): List<WorkSummary> = query(
        "SELECT * FROM book WHERE shelf = ? AND ('|' || subjects || '|') LIKE ? ORDER BY title COLLATE NOCASE, solo DESC, id",
        shelf, "%|$subject|%",
    ) { group(it) }

    suspend fun search(text: String): List<WorkSummary> {
        val terms = text.split(Regex("\\W+")).filter { it.isNotBlank() }
        if (terms.isEmpty()) return emptyList()
        val match = terms.joinToString(" ") { "$it*" }
        return query(
            "SELECT b.* FROM book_fts f JOIN book b ON b.id = f.docid WHERE book_fts MATCH ? ORDER BY b.solo DESC, b.id LIMIT 300",
            match,
        ) { group(it) }
    }

    suspend fun recordings(workId: Int): List<Recording> =
        query("SELECT * FROM book WHERE work_id = CAST(? AS INTEGER) ORDER BY solo DESC, id", workId.toString()) { c ->
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
        )
    }

    companion object {
        val SHELVES = listOf("Fiction", "Nonfiction", "Poetry & Plays", "Kids")
        const val EVERYTHING_ELSE = "Everything else"
    }
}
