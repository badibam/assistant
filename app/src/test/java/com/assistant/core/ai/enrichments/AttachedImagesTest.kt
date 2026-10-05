package com.assistant.core.ai.enrichments

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers what docs/design/message-images.md promised of a kept image: reduced to 1 568 px on its
 * longest side and never enlarged, decoded at a fraction that never falls under that size, and the
 * startup sweep removing only the parts and the images no row names.
 */
class AttachedImagesTest {

    @Test
    fun `a photo is reduced to 1568 px on its longest side, its ratio kept`() {
        assertEquals(1568 to 1176, AttachedImages.reducedSize(4000, 3000))
        assertEquals(1176 to 1568, AttachedImages.reducedSize(3000, 4000))
    }

    @Test
    fun `a small image is never enlarged`() {
        assertEquals(800 to 600, AttachedImages.reducedSize(800, 600))
        assertEquals(1568 to 1000, AttachedImages.reducedSize(1568, 1000))
    }

    @Test
    fun `the decoding fraction keeps the longest side at least 1568 px`() {
        assertEquals(1, AttachedImages.sampleSize(3000, 2000))
        assertEquals(2, AttachedImages.sampleSize(4000, 3000))
        assertEquals(4, AttachedImages.sampleSize(8160, 6120))
        assertEquals(1, AttachedImages.sampleSize(800, 600))
    }

    @Test
    fun `the sweep removes the parts and the images no row names, nothing else`() {
        val sweep = AttachedImages.sweep(
            names = listOf("a.jpg", "b.jpg", "c.jpg.part", "notes.txt"),
            ids = setOf("a")
        )
        assertEquals(listOf("c.jpg.part"), sweep.parts)
        assertEquals(listOf("b.jpg"), sweep.orphans)
    }
}
