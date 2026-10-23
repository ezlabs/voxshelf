package app.voxshelf

import android.content.Context

data class SavedPosition(val chapter: Int, val positionMs: Long, val pausedAt: Long)

/** Remembers the exact listening spot for each book, so it survives closing the app or restarting the phone. */
class PositionStore(context: Context) {
    private val prefs = context.getSharedPreferences("positions", Context.MODE_PRIVATE)

    fun load(bookId: Int): SavedPosition? {
        if (!prefs.contains("$bookId.chapter")) return null
        return SavedPosition(
            chapter = prefs.getInt("$bookId.chapter", 0),
            positionMs = prefs.getLong("$bookId.position", 0),
            pausedAt = prefs.getLong("$bookId.pausedAt", 0),
        )
    }

    fun save(bookId: Int, chapter: Int, positionMs: Long) {
        prefs.edit()
            .putInt("$bookId.chapter", chapter)
            .putLong("$bookId.position", positionMs)
            .putLong("$bookId.pausedAt", System.currentTimeMillis())
            .apply()
    }
}

/** Media ids look like "21507/3": the book id, then the chapter index. */
fun mediaId(bookId: Int, chapter: Int) = "$bookId/$chapter"

fun bookIdOf(mediaId: String?): Int? = mediaId?.substringBefore('/')?.toIntOrNull()
