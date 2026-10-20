package com.example.guardband.ui.display

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update

/**
 * Display (opened from its row in Settings): Use phone setting, Light or
 * Dark. The choice is saved first and then applied, so the Activities
 * AppCompat recreates in the new theme, and every later start
 * ([DisplayPrefs.applySaved]), see the same one.
 */
class DisplayViewModel(
    private val prefs: DisplayPrefs
) : ViewModel() {

    private val _uiState = MutableStateFlow(DisplayUiState(prefs.themeChoice))
    val uiState: StateFlow<DisplayUiState> = _uiState.asStateFlow()

    private val _events = Channel<DisplayEvent>(Channel.BUFFERED)
    val events: Flow<DisplayEvent> = _events.receiveAsFlow()

    /** An option was tapped. Tapping the one already selected changes nothing. */
    fun onThemeChoiceSelected(choice: ThemeChoice) {
        if (choice == _uiState.value.themeChoice) return
        prefs.themeChoice = choice
        _uiState.update { it.copy(themeChoice = choice) }
        _events.trySend(DisplayEvent.ApplyTheme(choice))
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                DisplayViewModel(DisplayPrefs.from(checkNotNull(this[APPLICATION_KEY])))
            }
        }
    }
}
