package com.example.guardband.ui.display

import androidx.appcompat.app.AppCompatDelegate
import com.example.guardband.testing.FakeDisplayPrefs
import com.example.guardband.testing.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Display: the theme option shown, saved and applied, and the stored name
 * read back, including one this build does not know.
 */
class DisplayViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `the phone's setting is the default`() {
        assertEquals(ThemeChoice.SYSTEM, ThemeChoice.DEFAULT)
        assertEquals(ThemeChoice.SYSTEM, DisplayViewModel(FakeDisplayPrefs()).uiState.value.themeChoice)
    }

    @Test
    fun `the saved choice is shown when the screen opens`() {
        val viewModel = DisplayViewModel(FakeDisplayPrefs(ThemeChoice.DARK))

        assertEquals(ThemeChoice.DARK, viewModel.uiState.value.themeChoice)
    }

    @Test
    fun `choosing a theme saves it, shows it and applies it`() = runTest {
        val prefs = FakeDisplayPrefs()
        val viewModel = DisplayViewModel(prefs)

        viewModel.onThemeChoiceSelected(ThemeChoice.DARK)

        assertEquals(ThemeChoice.DARK, prefs.themeChoice)
        assertEquals(ThemeChoice.DARK, viewModel.uiState.value.themeChoice)
        assertEquals(DisplayEvent.ApplyTheme(ThemeChoice.DARK), viewModel.events.first())
    }

    /** Showing the saved option checks its radio button; that must not count as a new choice. */
    @Test
    fun `choosing the one already selected changes nothing`() {
        val prefs = FakeDisplayPrefs(ThemeChoice.LIGHT)
        val viewModel = DisplayViewModel(prefs)

        viewModel.onThemeChoiceSelected(ThemeChoice.LIGHT)

        assertEquals(0, prefs.writes)
    }

    @Test
    fun `each choice maps to AppCompat's night mode`() {
        assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, ThemeChoice.SYSTEM.nightMode)
        assertEquals(AppCompatDelegate.MODE_NIGHT_NO, ThemeChoice.LIGHT.nightMode)
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, ThemeChoice.DARK.nightMode)
    }

    @Test
    fun `a stored name reads back, and an unknown or missing one is the default`() {
        assertEquals(ThemeChoice.DARK, ThemeChoice.fromStored("DARK"))
        assertEquals(ThemeChoice.SYSTEM, ThemeChoice.fromStored("SEPIA"))
        assertEquals(ThemeChoice.SYSTEM, ThemeChoice.fromStored(null))
    }
}
