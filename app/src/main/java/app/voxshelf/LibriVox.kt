package app.voxshelf

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class Chapter(val title: String, val url: String, val seconds: Int)

data class Book(
    val id: Int,
    val title: String,
    val author: String,
    val readers: List<String>,
    val chapters: List<Chapter>,
) {
    val totalSeconds: Int get() = chapters.sumOf { it.seconds }
}

/** Fetches one recording's details and chapter list from the LibriVox API. */
object LibriVox {
    private const val API = "https://librivox.org/api/feed/audiobooks"

    // LibriVox marks repeat recordings in the title, e.g. "(Version 2)"; the app shows those details separately.
    private val VARIANT_LABEL = Regex("""\s*\([^)]*\b(version \d+|dramatic reading|abridged)\b[^)]*\)\s*""", RegexOption.IGNORE_CASE)

    /** Keeps a copy of each book's details in [dir] so saved books still open without internet. */
    suspend fun book(id: Int, dir: File): Book = withContext(Dispatchers.IO) {
        val saved = File(dir, "books/$id.json")
        val body = try {
            val connection = URL("$API?id=$id&extended=1&format=json").openConnection() as HttpURLConnection
            connection.setRequestProperty("User-Agent", "Voxshelf/0.1 (open source audiobook app)")
            connection.inputStream.bufferedReader().use { it.readText() }.also {
                saved.parentFile?.mkdirs()
                saved.writeText(it)
            }
        } catch (e: java.io.IOException) {
            if (saved.exists()) saved.readText() else throw e
        }
        val json = JSONObject(body).getJSONArray("books").getJSONObject(0)

        val authors = json.getJSONArray("authors")
        val author = (0 until authors.length()).joinToString(", ") {
            val a = authors.getJSONObject(it)
            "${a.optString("first_name")} ${a.optString("last_name")}".trim()
        }
        val sections = json.getJSONArray("sections")
        val readers = linkedSetOf<String>()
        val chapters = (0 until sections.length()).map { i ->
            val s = sections.getJSONObject(i)
            val r = s.optJSONArray("readers")
            if (r != null) for (j in 0 until r.length()) readers += r.getJSONObject(j).optString("display_name")
            Chapter(
                title = s.optString("title").trim(),
                url = s.optString("listen_url").replace("http://", "https://"),
                seconds = s.optString("playtime").toIntOrNull() ?: 0,
            )
        }
        val title = json.optString("title").replace(VARIANT_LABEL, " ").trim()
        Book(id, title, author, readers.filter { it.isNotBlank() }, chapters)
    }
}
