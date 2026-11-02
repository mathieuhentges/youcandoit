package dev.mathieu.youcandoit.ui

import dev.mathieu.youcandoit.domain.model.CategoryId
import dev.mathieu.youcandoit.domain.model.TaskList
import dev.mathieu.youcandoit.ui.navigation.ListKey
import kotlin.test.assertEquals
import org.junit.Test

class ListKeyTest {
    @Test
    fun `every list survives a round trip through the route`() {
        listOf(TaskList.All, TaskList.Uncategorized, TaskList.InCategory(CategoryId("8f14e45f-ceea-4e7a-9f1b-2c3d4e5f6a7b")))
            .forEach { assertEquals(it, ListKey.decode(ListKey.encode(it))) }
    }

    @Test
    fun `a missing or unknown key means Home`() {
        assertEquals(TaskList.All, ListKey.decode(null))
        assertEquals(TaskList.All, ListKey.decode("???"))
    }
}
