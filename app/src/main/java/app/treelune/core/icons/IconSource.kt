package app.treelune.core.icons

/**
 * Where a theme's icons come from.
 *
 * Every theme shows the same vocabulary -- all of Lucide, by Lucide's names. What it chooses is
 * who draws them. There is no third way: a theme that draws some icons and borrows the rest
 * from Lucide would mix two styles on one screen, so scripts/generate_icons.py refuses a theme
 * that does not draw every one.
 */
enum class IconSource {
    /** Lucide's own drawings, the lucide_<name> drawables. */
    LUCIDE,

    /** The theme's drawings of every Lucide icon, the <theme>_<name> drawables made from its icons/ folder. */
    OWN
}
