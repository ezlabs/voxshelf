package app.voxshelf

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Sends a problem or idea from Settings to the Voxshelf server, which posts it as a public GitHub issue. */
object Feedback {
    enum class Kind(val key: String) { Problem("problem"), Idea("idea") }

    val enabled get() = BuildConfig.RATINGS_URL.isNotEmpty()

    /** Returns the GitHub issue number. Problems also carry the app version and phone model, to help fix them. */
    suspend fun send(kind: Kind, text: String): Int = withContext(Dispatchers.IO) {
        val body = JSONObject().put("kind", kind.key).put("text", text.trim())
        if (kind == Kind.Problem) body
            .put("version", BuildConfig.VERSION_NAME)
            .put("android", Build.VERSION.RELEASE)
            .put("model", "${Build.MANUFACTURER} ${Build.MODEL}")
        val connection = (URL("${BuildConfig.RATINGS_URL}/feedback").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("User-Agent", "Voxshelf/0.1 (https://voxshelf.app; hello@voxshelf.app)")
        }
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray()) }
            check(connection.responseCode in 200..299) { "Server said ${connection.responseCode}" }
            JSONObject(connection.inputStream.bufferedReader().use { it.readText() }).getInt("issue")
        } finally {
            connection.disconnect()
        }
    }
}
