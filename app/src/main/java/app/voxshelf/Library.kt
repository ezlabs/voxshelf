package app.voxshelf

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

enum class Status(val label: String) {
    Listening("Listening"),
    Want("To listen"),
    Finished("Finished"),
}

data class LibraryEntry(val workId: Int, val recordingId: Int, val status: Status, val updatedAt: Long)

data class UserList(val id: Long, val name: String, val books: Int)

/**
 * Your own library, kept only on this phone: what you're listening to, want to listen to and have finished,
 * plus lists you make. Separate from the catalog so catalog updates never touch it.
 */
class Library(context: Context) : SQLiteOpenHelper(context, "library.db", null, 1) {

    /** Bumps whenever anything changes, so screens know to reload. */
    private val _changes = MutableStateFlow(0)
    val changes: StateFlow<Int> = _changes.asStateFlow()

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE entry (work_id INTEGER PRIMARY KEY, recording_id INTEGER NOT NULL, status TEXT NOT NULL, updated_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE list (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, created_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE list_item (list_id INTEGER NOT NULL, work_id INTEGER NOT NULL, recording_id INTEGER NOT NULL, added_at INTEGER NOT NULL, PRIMARY KEY (list_id, work_id))")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    suspend fun entry(workId: Int): LibraryEntry? = io {
        readableDatabase.rawQuery("SELECT * FROM entry WHERE work_id = ?", arrayOf(workId.toString())).use { c ->
            if (c.moveToNext()) LibraryEntry(c.getInt(0), c.getInt(1), Status.valueOf(c.getString(2)), c.getLong(3)) else null
        }
    }

    suspend fun entries(status: Status): List<LibraryEntry> = io {
        readableDatabase.rawQuery("SELECT * FROM entry WHERE status = ? ORDER BY updated_at DESC", arrayOf(status.name)).use { c ->
            buildList { while (c.moveToNext()) add(LibraryEntry(c.getInt(0), c.getInt(1), status, c.getLong(3))) }
        }
    }

    suspend fun setStatus(workId: Int, recordingId: Int, status: Status?) = write {
        if (status == null) delete("entry", "work_id = ?", arrayOf(workId.toString()))
        else insertWithOnConflict("entry", null, ContentValues().apply {
            put("work_id", workId)
            put("recording_id", recordingId)
            put("status", status.name)
            put("updated_at", System.currentTimeMillis())
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    suspend fun lists(): List<UserList> = io {
        readableDatabase.rawQuery(
            "SELECT l.id, l.name, COUNT(i.work_id) FROM list l LEFT JOIN list_item i ON i.list_id = l.id GROUP BY l.id ORDER BY l.created_at DESC",
            null,
        ).use { c -> buildList { while (c.moveToNext()) add(UserList(c.getLong(0), c.getString(1), c.getInt(2))) } }
    }

    suspend fun createList(name: String): Long = write {
        insert("list", null, ContentValues().apply {
            put("name", name.trim())
            put("created_at", System.currentTimeMillis())
        })
    }

    suspend fun renameList(id: Long, name: String) = write {
        update("list", ContentValues().apply { put("name", name.trim()) }, "id = ?", arrayOf(id.toString()))
    }

    suspend fun deleteList(id: Long) = write {
        delete("list_item", "list_id = ?", arrayOf(id.toString()))
        delete("list", "id = ?", arrayOf(id.toString()))
    }

    /** Which of your lists a book is on. */
    suspend fun listsFor(workId: Int): Set<Long> = io {
        readableDatabase.rawQuery("SELECT list_id FROM list_item WHERE work_id = ?", arrayOf(workId.toString())).use { c ->
            buildSet { while (c.moveToNext()) add(c.getLong(0)) }
        }
    }

    suspend fun setOnList(listId: Long, workId: Int, recordingId: Int, on: Boolean) = write {
        if (on) insertWithOnConflict("list_item", null, ContentValues().apply {
            put("list_id", listId)
            put("work_id", workId)
            put("recording_id", recordingId)
            put("added_at", System.currentTimeMillis())
        }, SQLiteDatabase.CONFLICT_IGNORE)
        else delete("list_item", "list_id = ? AND work_id = ?", arrayOf(listId.toString(), workId.toString()))
    }

    suspend fun listItems(listId: Long): List<Int> = io {
        readableDatabase.rawQuery("SELECT recording_id FROM list_item WHERE list_id = ? ORDER BY added_at DESC", arrayOf(listId.toString())).use { c ->
            buildList { while (c.moveToNext()) add(c.getInt(0)) }
        }
    }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    private suspend fun <T> write(block: SQLiteDatabase.() -> T): T = io { writableDatabase.block() }.also { _changes.value++ }

    companion object {
        @Volatile private var instance: Library? = null
        fun get(context: Context) = instance ?: synchronized(this) {
            instance ?: Library(context.applicationContext).also { instance = it }
        }
    }
}
