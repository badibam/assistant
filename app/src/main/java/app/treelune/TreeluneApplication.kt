package app.treelune

import android.app.Application
import app.treelune.core.bugreport.CrashFile
import app.treelune.core.scheduling.CoreScheduler
import app.treelune.core.utils.AppConfigManager
import app.treelune.core.utils.LogManager

/**
 * The process: what must be in place before anything else runs, whatever starts it (the
 * activity, a scheduled worker, the external access service).
 */
class TreeluneApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // First: a crash of anything after this is kept for the next start's report
        CrashFile.install(this)
        // The logs kept in the database, whatever runs: a screen, the scheduler's alarm, the access
        LogManager.initialize(this)
        // The config cache: the theme, the timezone and the day's start, which the tools' schedulers read too
        AppConfigManager.initialize(this)
        // The scheduler's tick may be the first thing to run, when the alarm starts the process
        CoreScheduler.attach(this)
    }
}
