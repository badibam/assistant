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
     * The drawable for [iconName] in the current theme, or null when the name designates no
     * icon -- the caller shows its two first letters instead. A former Lucide name finds the
     * icon it became.
     */
    fun drawable(context: Context, iconName: String): Int? {
        val name = index(context).resolve(iconName) ?: return null
        val prefix = when (CurrentTheme.current.iconSource) {
            IconSource.LUCIDE -> "lucide"
            IconSource.OWN -> CurrentTheme.getCurrentThemeId()
        }
        val id = context.resources.getIdentifier("${prefix}_${name.replace('-', '_')}", "drawable", context.packageName)
        return id.takeIf { it != 0 }
    }
}
