package com.shawnkowalchuk.milo.core.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Which slice of a tile each row of a list draws. */
class TileRowsTest {
    private fun places(count: Int) = (0 until count).map { tileRowPlace(it, count) }

    @Test
    fun `one row is the whole tile`() {
        assertEquals(listOf(TileRowPlace.ONLY), places(1))
    }

    @Test
    fun `two rows are the tile's top and its bottom`() {
        assertEquals(listOf(TileRowPlace.FIRST, TileRowPlace.LAST), places(2))
    }

    @Test
    fun `the rows between the first and the last have no round corner`() {
        assertEquals(
            listOf(
                TileRowPlace.FIRST,
                TileRowPlace.MIDDLE,
                TileRowPlace.MIDDLE,
                TileRowPlace.LAST,
            ),
            places(4),
        )
    }

    @Test
    fun `a row that is not one of the tile's rows is refused`() {
        assertThrows(IllegalArgumentException::class.java) { tileRowPlace(0, 0) }
        assertThrows(IllegalArgumentException::class.java) { tileRowPlace(3, 3) }
        assertThrows(IllegalArgumentException::class.java) { tileRowPlace(-1, 3) }
    }
}
