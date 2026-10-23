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
            connection.setRequestProperty("User-Agent", "Voxshelf/0.1 (https://voxshelf.app; hello@voxshelf.app)")
            connection.inputStream.bufferedReader().use { it.readText() }.also {
                saved.parentFile?.mkdirs()
                saved.writeText(it)
            }
        } catch (e: java.io.IOException) {
            if (saved.exists()) saved.readText() else throw e
        }
        parse(id, body)
    }

    /** The copy saved when you last opened this book, without going online. Null if you never have. */
    suspend fun saved(id: Int, dir: File): Book? = withContext(Dispatchers.IO) {
        File(dir, "books/$id.json").takeIf { it.exists() }?.let { runCatching { parse(id, it.readText()) }.getOrNull() }
    }

    private fun parse(id: Int, body: String): Book {
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
                title = chapterName(s.optString("title"), i),
                url = s.optString("listen_url").replace("http://", "https://"),
                seconds = s.optString("playtime").toIntOrNull() ?: 0,
            )
        }
        val title = json.optString("title").replace(VARIANT_LABEL, " ").trim()
        return Book(id, title, author, readers.filter { it.isNotBlank() }, chapters)
    }
}

/**
 * A tidy chapter name from LibriVox's raw section title: drops track numbers ("05 - ") and leading
 * chapter labels when a real name follows, fixes ALL CAPS, and falls back to "Chapter N" when there's no name.
 */
fun chapterName(raw: String?, index: Int): String {
    var t = raw.orEmpty().replace(Regex("""\s+"""), " ").trim()
    if (t.isEmpty() || t == "null" || Regex("""(?i)^(section|secci.n|track|part)\s*\d+$""").matches(t)) return "Chapter ${index + 1}"
    // "05 - Name", "5. Name", "XV. Name"
    t = t.replace(Regex("""^(\d{1,3}|[IVXLC]{1,7})\s*[-.:)]\s+(?=\S)"""), "")
    // "Chapter 3: Name", "Chapter XX - Name", "Chapter 3 Name" (only when a name follows)
    t = t.replace(Regex("""(?i)^chapter\s+([0-9]+|[ivxlc]+)\s*[-.:]?\s+(?=[A-Za-z"'])"""), "")
    if (t.length > 3 && t == t.uppercase() && t.any { it.isLetter() }) {
        t = t.lowercase().split(" ").joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
    }
    // "Chapter XX" on its own reads better as "Chapter 20".
    Regex("""(?i)^chapter\s+([ivxlc]+)$""").find(t)?.let { m -> roman(m.groupValues[1])?.let { return "Chapter $it" } }
    return t.ifBlank { "Chapter ${index + 1}" }
}

private fun roman(s: String): Int? {
    val values = mapOf('i' to 1, 'v' to 5, 'x' to 10, 'l' to 50, 'c' to 100)
    var total = 0
    s.lowercase().forEachIndexed { i, ch ->
        val v = values[ch] ?: return null
        val next = s.lowercase().getOrNull(i + 1)?.let { values[it] } ?: 0
        total += if (v < next) -v else v
    }
    return total.takeIf { it > 0 }
}
