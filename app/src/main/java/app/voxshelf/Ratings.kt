package app.voxshelf

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/** The problems you can report on a recording, as the server names them. */
enum class Problem(val key: String, val label: String) {
    Sound("sound", "Sound quality (noise, volume, echo)"),
    HardToFollow("hard_to_follow", "Hard to follow the reading"),
    Missing("missing", "Missing or repeated parts"),
    WontPlay("wont_play", "A chapter won't play"),
    Order("order", "Chapters out of order or mislabeled"),
    Other("other", "Something else"),
}

/** What others thought of a recording. Likes and problems are only known once enough people have rated it. */
data class RatingSummary(val total: Int, val up: Int?, val problems: Map<Problem, Int>) {
    val percentLiked: Int? get() = up?.let { it * 100 / total }
}

/**
 * Thumbs up or down on recordings, shared with everyone through the Voxshelf ratings server.
 * Each phone has an anonymous id so it gets one rating per recording.
 */
class Ratings(context: Context) {
    private val prefs = context.getSharedPreferences("ratings", Context.MODE_PRIVATE)
    private val device: String = prefs.getString("device", null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("device", it).apply() }

    val enabled get() = BuildConfig.RATINGS_URL.isNotEmpty()

    /** Whether we've already asked about this recording (we only ask once). */
    fun asked(recording: Int) = prefs.getBoolean("asked/$recording", false)
    fun markAsked(recording: Int) = prefs.edit().putBoolean("asked/$recording", true).apply()

    suspend fun send(recording: Int, up: Boolean, problems: Set<Problem> = emptySet(), note: String = "", chapter: Int? = null) = io {
        markAsked(recording)
        val body = JSONObject()
            .put("device", device)
            .put("recording", recording)
            .put("up", up)
            .put("problems", JSONArray(problems.map { it.key }))
            .put("note", note.trim())
        if (chapter != null) body.put("chapter", chapter)
        val c = connect("/ratings")
        c.requestMethod = "POST"
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        c.outputStream.use { it.write(body.toString().toByteArray()) }
        check(c.responseCode == 200) { "Rating not saved (${c.responseCode})" }
    }

    suspend fun summaries(recordings: List<Int>): Map<Int, RatingSummary> = io {
        if (recordings.isEmpty()) return@io emptyMap()
        val json = JSONObject(connect("/ratings?ids=${recordings.joinToString(",")}").inputStream.bufferedReader().use { it.readText() })
        json.keys().asSequence().associate { id ->
            val r = json.getJSONObject(id)
            val problems = r.optJSONObject("problems")
            id.toInt() to RatingSummary(
                total = r.getInt("total"),
                up = if (r.has("up")) r.getInt("up") else null,
                problems = Problem.entries.mapNotNull { p -> problems?.optInt(p.key)?.takeIf { it > 0 }?.let { p to it } }.toMap(),
            )
        }
    }

    private fun connect(path: String) = (URL(BuildConfig.RATINGS_URL + path).openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000
        readTimeout = 10_000
        setRequestProperty("User-Agent", "Voxshelf/0.1 (open source audiobook app)")
    }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    companion object {
        @Volatile private var instance: Ratings? = null
        fun get(context: Context) = instance ?: synchronized(this) {
            instance ?: Ratings(context.applicationContext).also { instance = it }
        }
    }
}
