package app.voxshelf

import android.content.ContentProvider
import android.content.ContentValues
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Serves cover art to Android Auto, which can't load images from the web itself. Each cover is fetched once
 * from covers.voxshelf.app and kept in the app's cache: content://app.voxshelf.art/123
 */
class CarArtProvider : ContentProvider() {
    override fun onCreate() = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val id = uri.lastPathSegment?.toIntOrNull() ?: throw FileNotFoundException(uri.toString())
        val context = context ?: throw FileNotFoundException(uri.toString())
        val file = File(File(context.cacheDir, "car-art").apply { mkdirs() }, "$id.webp")
        if (!file.exists()) {
            val connection = URL(Art(id, "").fast).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 10_000
                connection.readTimeout = 15_000
                if (connection.responseCode !in 200..299) throw FileNotFoundException(uri.toString())
                val tmp = File(file.path + ".part")
                connection.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                tmp.renameTo(file)
            } finally {
                connection.disconnect()
            }
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri) = "image/webp"
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?) = null
    override fun insert(uri: Uri, values: ContentValues?) = null
    override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0

    companion object {
        private const val AUTHORITY = "app.voxshelf.art"
        fun uriFor(recordingId: Int): Uri = Uri.parse("content://$AUTHORITY/$recordingId")
    }
}
