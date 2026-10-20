package com.example.guardband.ui.display

import android.os.Bundle
import android.widget.ImageButton
import android.widget.RadioGroup
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.guardband.R
import kotlinx.coroutines.launch

/**
 * Display: the app's theme, Use phone setting (the default), Light or Dark.
 * Opened from the Display row in Settings.
 *
 * The change is immediate: AppCompat recreates this screen and the ones
 * behind it in the new theme, and the ViewModel, which survives that, has
 * already saved it.
 *
 * An Activity rather than a Fragment pushed over the Home host, for the
 * reason Changelog and About are: Back from here has to return to Settings.
 *
 * View ids: ib_display_back, tv_display_title, tv_display_theme_label,
 * rg_display_theme, rb_display_theme_system, rb_display_theme_light,
 * rb_display_theme_dark, tv_display_theme_help.
 *
 * Flow: Settings → DisplayActivity → (Back) Settings
 */
class DisplayActivity : AppCompatActivity() {

    private val viewModel: DisplayViewModel by viewModels { DisplayViewModel.Factory }

    private lateinit var themeGroup: RadioGroup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_display)

        findViewById<ImageButton>(R.id.ib_display_back).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        themeGroup = findViewById(R.id.rg_display_theme)
        // Rendered before the listener goes on, so showing the saved choice
        // never counts as the user picking it.
        render(viewModel.uiState.value)
        themeGroup.setOnCheckedChangeListener { _, checkedId ->
            choiceFor(checkedId)?.let(viewModel::onThemeChoiceSelected)
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect(::handleEvent)
            }
        }
    }

    private fun render(state: DisplayUiState) {
        val id = idFor(state.themeChoice)
        if (themeGroup.checkedRadioButtonId != id) themeGroup.check(id)
    }

    private fun handleEvent(event: DisplayEvent) {
        when (event) {
            is DisplayEvent.ApplyTheme ->
                AppCompatDelegate.setDefaultNightMode(event.choice.nightMode)
        }
    }

    private fun idFor(choice: ThemeChoice): Int = when (choice) {
        ThemeChoice.SYSTEM -> R.id.rb_display_theme_system
        ThemeChoice.LIGHT -> R.id.rb_display_theme_light
        ThemeChoice.DARK -> R.id.rb_display_theme_dark
    }

    private fun choiceFor(id: Int): ThemeChoice? = when (id) {
        R.id.rb_display_theme_system -> ThemeChoice.SYSTEM
        R.id.rb_display_theme_light -> ThemeChoice.LIGHT
        R.id.rb_display_theme_dark -> ThemeChoice.DARK
        else -> null
    }
}
