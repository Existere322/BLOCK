package app.shijie.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageCategoriesTest {
    @Test
    fun assignsGroupColorAndLeavesTheRestAsOther() {
        val shop = 0xFF112233.toInt()
        val play = 0xFF445566.toInt()
        val membership = mapOf(
            "shop.app" to listOf("购物" to shop),
            "play.app" to listOf("娱乐" to play),
        )
        val order = listOf("购物" to shop, "娱乐" to play)
        val (columns, legend) = UsageCategories.stack(
            labels = listOf("00", "01"),
            perBucket = listOf(
                mapOf("shop.app" to 10L, "mail.app" to 5L),
                mapOf("play.app" to 7L),
            ),
            membership = membership,
            groupOrder = order,
        )
        assertEquals(listOf("其他", "购物"), columns[0].slices.map { it.name })
        assertEquals(5L, columns[0].slices[0].millis)
        assertEquals(UsageCategories.OTHER_COLOR, columns[0].slices[0].colorArgb)
        assertEquals(shop, columns[0].slices[1].colorArgb)
        assertEquals(listOf("娱乐"), columns[1].slices.map { it.name })
        assertEquals(listOf("其他", "购物", "娱乐"), legend.map { it.name })
        assertEquals(5L, legend[0].millis)
        assertEquals(10L, legend[1].millis)
        assertEquals(7L, legend[2].millis)
        assertEquals(15L, columns[0].uniqueMillis)
    }

    @Test
    fun groupedAppIsExcludedFromOther() {
        val (_, legend) = UsageCategories.stack(
            labels = listOf("00"),
            perBucket = listOf(mapOf("shop.app" to 10L, "mail.app" to 4L)),
            membership = mapOf("shop.app" to listOf("购物" to 1)),
            groupOrder = listOf("购物" to 1),
        )
        assertEquals(4L, legend.first { it.name == "其他" }.millis)
        assertEquals(10L, legend.first { it.name == "购物" }.millis)
    }

    @Test
    fun emptyBucketHasNoSlices() {
        val (columns, legend) = UsageCategories.stack(
            labels = listOf("00"),
            perBucket = listOf(emptyMap()),
            membership = emptyMap(),
            groupOrder = emptyList(),
        )
        assertTrue(columns.single().slices.isEmpty())
        assertTrue(legend.isEmpty())
    }
}
