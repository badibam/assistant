package com.assistant.core.icons

import com.assistant.core.ai.data.EnrichmentType
import com.assistant.core.ui.ButtonAction
import com.assistant.tools.journal.JournalToolType
import com.assistant.tools.messages.MessageToolType
import com.assistant.tools.notes.NotesToolType
import com.assistant.tools.tracking.TrackingToolType
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Every icon name the code asks for is in the vocabulary. A name that is not shows as two
 * letters, or breaks an action button, on the device only: this finds it before.
 */
class IconNamesInCodeTest {

    private val index = IconIndex.parse(File("src/main/assets/icons/index.json").readText())

    /** The names the code asks for, with where each comes from. */
    private fun namesInCode(): List<Pair<String, String>> {
        val names = mutableListOf<Pair<String, String>>()
        ButtonAction.entries.forEach { names.add("ButtonAction.$it" to it.iconName) }
        EnrichmentType.entries.forEach { names.add("EnrichmentType.$it" to it.iconName) }
        listOf(JournalToolType, MessageToolType, NotesToolType, TrackingToolType).forEach { toolType ->
            val owner = toolType::class.simpleName
            names.add("$owner default" to toolType.getDefaultIconName())
            toolType.getSuggestedIcons().forEach { names.add("$owner suggested" to it) }
        }
        // The names written in the screens: UI.Icon(iconName = "...")
        val literal = Regex("""iconName = "([^"]+)"""")
        File("src/main/java").walkTopDown().filter { it.extension == "kt" }.forEach { file ->
            literal.findAll(file.readText()).forEach { names.add(file.name to it.groupValues[1]) }
        }
        return names
    }

    /** A current name, not a former one: the code writes the names it means today. */
    @Test
    fun everyIconNameInTheCodeIsACurrentLucideName() {
        val names = namesInCode()
        val wrong = names.filter { (_, name) -> index.resolve(name) != name }
        assertTrue(wrong.joinToString("\n") { (where, name) -> "$where: $name" }, wrong.isEmpty())
        assertTrue("the source scan found no name", names.any { it.first.endsWith(".kt") })
    }
}
