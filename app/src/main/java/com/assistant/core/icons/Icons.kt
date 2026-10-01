package com.assistant.core.icons

import android.content.Context
import com.assistant.core.themes.CurrentTheme

/**
 * The app's icons: the index, and the drawable that shows a name in the current theme.
 *
 * The index is read once from assets/icons/index.json, on first use.
 */
object Icons {

    @Volatile
    private var loaded: IconIndex? = null

    /** The icon vocabulary. */
    fun index(context: Context): IconIndex =
        loaded ?: synchronized(this) {
            loaded ?: IconIndex.parse(
                context.applicationContext.assets.open("icons/index.json").bufferedReader().use { it.readText() }
            ).also { loaded = it }
        }

    /**
     * What an icon name becomes when a zone or a tool stores it: itself when current, the
     * current name when it is a former one, null when it designates no icon -- which the
     * caller refuses. Stored names are therefore always current ones.
     */
    fun storedName(context: Context, iconName: String): String? = index(context).resolve(iconName)

    /**
     * The drawable for [iconName] in the current theme, or null when the name designates no
     * icon -- the caller shows its two first letters instead. A former Lucide name finds the
     * icon it became.
     */
    fun drawable(context: Context, iconName: String): Int? = when (CurrentTheme.current.iconSource) {
        IconSource.LUCIDE -> drawableOf(context, "lucide", iconName)
        IconSource.OWN -> drawableOf(context, CurrentTheme.getCurrentThemeId(), iconName)
    }

    /**
     * Lucide's drawing of [iconName], whatever the theme, or null when the name designates no
     * icon. For the places a theme does not reach, such as the status bar, which keeps only an
     * icon's silhouette and scales it by no whole factor.
     */
    fun lucideDrawable(context: Context, iconName: String): Int? = drawableOf(context, "lucide", iconName)

    private fun drawableOf(context: Context, prefix: String, iconName: String): Int? {
        val name = index(context).resolve(iconName) ?: return null
        val id = context.resources.getIdentifier("${prefix}_${name.replace('-', '_')}", "drawable", context.packageName)
        return id.takeIf { it != 0 }
    }
}
