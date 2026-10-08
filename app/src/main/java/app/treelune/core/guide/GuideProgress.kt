package app.treelune.core.guide

import app.treelune.core.config.AppSettings
import org.json.JSONArray
import org.json.JSONObject

/** Where a chapter stands: the step reached (from 0), done or not, and what its steps kept by name. */
data class ChapterProgress(val id: String, val step: Int, val done: Boolean, val kept: Map<String, String>)

/**
 * The Guide's progress, kept in the app's settings (category guide): it survives updates and the
 * demo's reinstall, leaves with a backup, and goes with a reset of the app, which brings the
 * first-launch screen back.
 *
 * @property current The tutorial in progress, whose step the band shows
 */
data class GuideProgress(
    val welcomeSeen: Boolean,
    val bandHidden: Boolean,
    val current: String?,
    val chapters: Map<String, ChapterProgress>
) {
    fun of(id: String): ChapterProgress? = chapters[id]

    fun with(chapter: ChapterProgress) = copy(chapters = chapters + (chapter.id to chapter))

    fun toJson(): JSONObject = JSONObject()
        .put(AppSettings.GUIDE_WELCOME_SEEN, welcomeSeen)
        .put(AppSettings.GUIDE_BAND_HIDDEN, bandHidden)
        .apply { current?.let { put(AppSettings.GUIDE_CURRENT, it) } }
        .put(AppSettings.GUIDE_CHAPTERS, JSONArray(chapters.values.map { c ->
            JSONObject().put("id", c.id).put("step", c.step).put("done", c.done)
                .put("kept", JSONArray(c.kept.map { (name, value) -> JSONObject().put("name", name).put("value", value) }))
        }))

    companion object {
        /** The progress as stored; a required key it lacks is an error, as for any category. */
        fun fromJson(json: JSONObject): GuideProgress {
            val chapters = json.getJSONArray(AppSettings.GUIDE_CHAPTERS)
            return GuideProgress(
                welcomeSeen = json.getBoolean(AppSettings.GUIDE_WELCOME_SEEN),
                bandHidden = json.getBoolean(AppSettings.GUIDE_BAND_HIDDEN),
                current = json.optString(AppSettings.GUIDE_CURRENT).ifEmpty { null },
                chapters = (0 until chapters.length()).map { i ->
                    val c = chapters.getJSONObject(i)
                    val kept = c.getJSONArray("kept")
                    ChapterProgress(
                        id = c.getString("id"),
                        step = c.getInt("step"),
                        done = c.getBoolean("done"),
                        kept = (0 until kept.length()).associate { k -> kept.getJSONObject(k).let { it.getString("name") to it.getString("value") } }
                    )
                }.associateBy { it.id }
            )
        }
    }
}
