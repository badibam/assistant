package com.assistant.core.demo

import android.content.Context
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.database.entities.AppSettingCategories
import com.assistant.core.utils.LogManager
import java.io.File

/**
 * The demo at the app's start (docs/design/demo.md): installed afresh once per installation of
 * the app, which `PackageInfo.lastUpdateTime` marks — a release as a debug build, an update as a
 * first install. The installation the demo was last installed for is kept in a file of the app's
 * own, which only this reads.
 */
object DemoStartup {

    /** The setting under which the demo is installed at each update. */
    const val INSTALL_ON_UPDATE = "install_on_update"

    private const val STAMP_FILE = "demo_installed_for"

    /**
     * Installs the demo when the app was installed since it last was and the setting is on; off,
     * the demo there is stays as it is — removing it is the user's button. Null when done or
     * nothing was to do; the error to show once otherwise — the installation is marked all the
     * same, so the error is not shown at every start.
     */
    suspend fun run(context: Context): String? {
        val installedAt = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
        if (readStamp(context) == installedAt) return null

        val coordinator = Coordinator(context)
        val settings = coordinator.processUserAction("app_config.get", mapOf("category" to AppSettingCategories.DEMO))
        val enabled = (settings.data?.get("settings") as? Map<*, *>)?.get(INSTALL_ON_UPDATE) as? Boolean
        val result = when (enabled) {
            null -> settings
            true -> coordinator.processUserAction("demo.install", emptyMap())
            false -> null
        }
        writeStamp(context, installedAt)
        if (result == null || result.isSuccess) return null
        LogManager.service("Demo at start failed: ${result.error}", "ERROR")
        return result.error
    }

    private fun readStamp(context: Context): Long? =
        File(context.filesDir, STAMP_FILE).takeIf { it.exists() }?.readText(Charsets.UTF_8)?.trim()?.toLongOrNull()

    /** Written to a temporary file then renamed: an interrupted write never leaves half a number. */
    private fun writeStamp(context: Context, installedAt: Long) {
        val part = File(context.filesDir, "$STAMP_FILE.part")
        part.writeText(installedAt.toString(), Charsets.UTF_8)
        check(part.renameTo(File(context.filesDir, STAMP_FILE))) { "Demo stamp not written" }
    }
}
