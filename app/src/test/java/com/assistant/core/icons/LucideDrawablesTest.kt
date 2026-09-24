package com.assistant.core.icons

import androidx.compose.ui.graphics.vector.PathParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Covers the generated lucide_* drawables as the app reads them: painterResource hands a vector
 * drawable's pathData to Compose's PathParser, the one run here.
 *
 * scripts/generate_icons.py rewrites every path with its numbers separated, because arc flags
 * written together ("a2 2 0 0022 17") are not read by every parser; this states that each path
 * of each icon parses, and that the index and the drawables hold the same icons.
 */
class LucideDrawablesTest {

    private val drawables = File("src/main/res/drawable").listFiles { _, name -> name.startsWith("lucide_") }!!.toList()

    @Test
    fun everyPathOfEveryIconParses() {
        val pathData = Regex("""android:pathData="([^"]+)"""")
        val failures = mutableListOf<String>()
        drawables.forEach { file ->
            pathData.findAll(file.readText()).forEach { match ->
                try {
                    val data = match.groupValues[1]
                    val nodes = PathParser().parsePathString(data).toNodes()
                    val expected = expectedSegments(data)
                    if (nodes.size != expected) failures.add("${file.name}: $expected segments written, ${nodes.size} parsed in \"$data\"")
                } catch (e: Exception) {
                    failures.add("${file.name}: ${e.message}")
                }
            }
        }
        assertTrue(failures.take(10).joinToString("\n"), failures.isEmpty())
    }

    /**
     * How many segments [data] writes: each command's parameters, taken by its arity, repeat
     * it; -1 when its numbers are not all separated. Compose's parser drops a segment it cannot read -- an arc whose flags run into the
     * next number -- without an error, so a parse is only complete if the counts agree.
     */
    private fun expectedSegments(data: String): Int {
        val arity = mapOf('m' to 2, 'l' to 2, 'h' to 1, 'v' to 1, 'c' to 6, 's' to 4, 'q' to 4, 't' to 2, 'a' to 7, 'z' to 0)
        var count = 0
        Regex("""([MmLlHhVvCcSsQqTtAaZz])([^MmLlHhVvCcSsQqTtAaZz]*)""").findAll(data).forEach { command ->
            val n = arity.getValue(command.groupValues[1].lowercase().single())
            val numbers = command.groupValues[2].trim().split(Regex("[\\s,]+")).count { it.isNotEmpty() }
            if (n == 0) { count += 1; return@forEach }
            // Numbers that do not divide by the arity were not all separated: "0022" is read
            // as one number where the arc means three. The count is then wrong by design.
            if (numbers == 0 || numbers % n != 0) return -1
            count += numbers / n
        }
        return count
    }

    @Test
    fun theDrawablesAreTheIndexsIcons() {
        val index = IconIndex.parse(File("src/main/assets/icons/index.json").readText())
        val drawn = drawables.map { it.name.removePrefix("lucide_").removeSuffix(".xml") }.toSet()
        assertEquals(index.icons.map { it.name.replace('-', '_') }.toSet(), drawn)
    }
}
