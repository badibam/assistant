package app.treelune

import android.app.Application
import app.treelune.core.bugreport.CrashFile

/**
 * The process: what must be in place before anything else runs, whatever starts it (the
 * activity, a scheduled worker, the external access service).
 */
class TreeluneApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // First: a crash of anything after this is kept for the next start's report
        CrashFile.install(this)
    }
}
