package app.voxshelf

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.ConnectivityManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

/**
 * Keeps the catalog fresh without app updates: about once a day, on Wi-Fi, checks the nightly catalog published
 * on GitHub and, if it's newer, downloads it (about 10 MB). The new catalog is used from the next launch.
 */
object CatalogUpdates {
    const val NEXT = "catalog-next.db"
    private const val RELEASE = "https://github.com/ezlabs/voxshelf/releases/download/catalog"
    private const val CHECK_EVERY_MS = 20 * 60 * 60 * 1000L

    fun checkInBackground(context: Context) {
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch { runCatching { check(app) }.onFailure { android.util.Log.w("CatalogUpdates", "check failed", it) } }
    }

    private fun check(context: Context) {
        val prefs = context.getSharedPreferences("catalog", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong("checkedAt", 0) < CHECK_EVERY_MS) return
        // Only on Wi-Fi (or another unmetered network), so it never eats into a data plan.
        if (context.getSystemService(ConnectivityManager::class.java)?.isActiveNetworkMetered != false) return

        val manifest = JSONObject(get("$RELEASE/manifest.json").use { it.bufferedReader().readText() })
        prefs.edit().putLong("checkedAt", now).apply()
        val latest = manifest.getString("version")
        val pending = File(context.filesDir, NEXT)
        val current = if (pending.exists()) Catalog.versionOf(pending) else Catalog.versionOf(File(context.filesDir, "catalog.db"))
        if (latest <= current) return

        val tmp = File(context.cacheDir, "catalog-download.db")
        get("$RELEASE/${manifest.getString("full")}").use { input ->
            GZIPInputStream(input).use { gz -> tmp.outputStream().use { gz.copyTo(it) } }
        }
        // Only keep it if it opens, is the version promised, and looks like a whole catalog this app can read.
        val ok = Catalog.versionOf(tmp) == latest && runCatching {
            SQLiteDatabase.openDatabase(tmp.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                // Also make sure it has the columns this version of the app reads.
                db.rawQuery("SELECT id, work_id, work_score, score, cover, year, recorded, secs FROM book LIMIT 1", null).close()
                db.rawQuery("SELECT COUNT(*) FROM book", null).use { it.moveToNext() && it.getInt(0) > 10_000 }
            }
        }.getOrDefault(false)
        if (ok) tmp.copyTo(pending, overwrite = true)
        tmp.delete()
    }

    private fun get(url: String) = (URL(url).openConnection() as HttpURLConnection).run {
        connectTimeout = 15_000
        readTimeout = 60_000
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", "Voxshelf/0.1 (https://voxshelf.app; hello@voxshelf.app)")
        check(responseCode in 200..299) { "GitHub said $responseCode" }
        inputStream
    }
}
