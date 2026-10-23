package app.voxshelf

import android.app.Application

class VoxshelfApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReports.install(this)
    }
}
