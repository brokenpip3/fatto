package com.brokenpip3.fatto

import com.brokenpip3.fatto.vm.ProjectNode
import com.brokenpip3.fatto.vm.selectableProjectNames
import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectChoicesTest {
    private fun node(
        name: String,
        pending: Int,
    ) = ProjectNode(name.substringAfterLast('.'), name, pending, 0, pending, name.count { it == '.' })

    @Test
    fun `empty projects follow setting and names stay sorted`() {
        val nodes = listOf(node("Work.Empty", 0), node("Home", 2), node("Work", 1))

        assertEquals(listOf("Home", "Work"), selectableProjectNames(nodes, false))
        assertEquals(listOf("Home", "Work", "Work.Empty"), selectableProjectNames(nodes, true))
    }
}
