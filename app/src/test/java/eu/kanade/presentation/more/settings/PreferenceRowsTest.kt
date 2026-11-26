package eu.kanade.presentation.more.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PreferenceRowsTest {
    @Test
    fun `hidden groups do not change a visible setting's scroll position`() {
        val disabled = Preference.PreferenceGroup(
            title = "Hidden",
            enabled = false,
            preferenceItems = listOf(Preference.PreferenceItem.TextPreference("Hidden setting")),
        )
        val target = Preference.PreferenceItem.TextPreference("Target setting")
        val visible = Preference.PreferenceGroup(
            title = "Shown",
            preferenceItems = listOf(Preference.PreferenceItem.TextPreference("Visible setting")),
        )

        val rows = listOf(disabled, visible, target).toVisibleRows()

        assertEquals(3, rows.indexOfFirst { it.preference === target })
        assertEquals(listOf("Shown", "Visible setting", null, "Target setting"), rows.map { it.preference?.title })
    }

    @Test
    fun `an identically titled visible group keeps its keys when another group is hidden`() {
        val firstItem = Preference.PreferenceItem.TextPreference("Status")
        val secondItem = Preference.PreferenceItem.TextPreference("Status")
        val first = Preference.PreferenceGroup(title = "Appearance", preferenceItems = listOf(firstItem))
        val second = Preference.PreferenceGroup(title = "Appearance", preferenceItems = listOf(secondItem))

        val bothVisible = listOf(first, second).toVisibleRows()
        val onlySecond = listOf(first.copy(enabled = false), second).toVisibleRows()

        assertEquals(
            bothVisible.first { it.preference === secondItem }.key,
            onlySecond.first { it.preference === secondItem }.key,
        )
        assertEquals(bothVisible.size, bothVisible.map { it.key }.toSet().size)
    }
}
