package com.assistant.core.ui.selectors

import com.assistant.core.fields.FieldDefinition
import com.assistant.core.fields.FieldType
import org.junit.Assert.assertEquals
import org.junit.Test

/** A field shows by its name, and by its name and path only where two fields share the name. */
class FieldLabelsTest {

    private fun field(label: String) = FieldDefinition("x", label, null, FieldType.TEXT, false, null)

    @Test
    fun aNameOfItsOwn_showsAlone() {
        assertEquals(
            mapOf("data.kcal" to "Calories", "name" to "Name"),
            fieldLabels(mapOf("data.kcal" to field("Calories"), "name" to field("Name")))
        )
    }

    @Test
    fun aSharedName_isToldApartByItsPath() {
        assertEquals(
            mapOf("data.note" to "Note (data.note)", "extra.note" to "Note (extra.note)", "name" to "Name"),
            fieldLabels(mapOf("data.note" to field("Note"), "extra.note" to field("Note"), "name" to field("Name")))
        )
    }
}
