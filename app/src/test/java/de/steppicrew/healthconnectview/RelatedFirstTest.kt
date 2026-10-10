package de.steppicrew.healthconnectview

import de.steppicrew.healthconnectview.registry.Category
import de.steppicrew.healthconnectview.ui.compare.relatedFirst
import org.junit.Assert.assertEquals
import org.junit.Test

class RelatedFirstTest {

    private val types = listOf(
        "Steps" to Category.ACTIVITY,
        "Weight" to Category.BODY,
        "Heart rate" to Category.VITALS,
        "Bone mass" to Category.BODY,
        "Distance" to Category.ACTIVITY,
        "Body fat" to Category.BODY,
    )

    private fun grouped(current: Category?) =
        relatedFirst(current, types, { it.second }, { it.first }).map { (category, members) -> category to members.map { it.first } }

    @Test
    fun `the current type's category comes first, the rest in catalog order`() {
        assertEquals(
            listOf(
                Category.BODY to listOf("Body fat", "Bone mass", "Weight"),
                Category.ACTIVITY to listOf("Distance", "Steps"),
                Category.VITALS to listOf("Heart rate"),
            ),
            grouped(Category.BODY),
        )
    }

    @Test
    fun `an unknown current type leaves the catalog order`() {
        assertEquals(listOf(Category.ACTIVITY, Category.BODY, Category.VITALS), grouped(null).map { it.first })
    }
}
