package app.voxshelf

import android.content.Context
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * When the app crashes, saves the error to a file; the next time the app starts, sends it to the
 * Voxshelf server, which files it as a GitHub issue. Only the error, app version, Android version
 * and phone model are sent.
 */
object CrashReports {
    private const val FILE = "crash.txt"

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { File(app.filesDir, FILE).writeText(error.stackTraceToString().take(8000)) }
            previous?.uncaughtException(thread, error)
        }
        sendSaved(app)
    }

    private fun sendSaved(context: Context) {
        val file = File(context.filesDir, FILE)
        if (!file.exists() || BuildConfig.RATINGS_URL.isEmpty()) return
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val body = JSONObject()
                    .put("trace", file.readText())
                    .put("version", BuildConfig.VERSION_NAME)
                    .put("android", Build.VERSION.RELEASE)
                    .put("model", "${Build.MANUFACTURER} ${Build.MODEL}")
                val connection = URL("${BuildConfig.RATINGS_URL}/crashes").openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray()) }
                // Delete once the server has it; on failure, try again next launch.
                if (connection.responseCode in 200..299) file.delete()
                connection.disconnect()
            }
        }
    }
}
