package com.example.guardband.ui.about

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.guardband.BuildConfig
import com.example.guardband.R
import kotlinx.coroutines.launch

/**
 * About: the app's logo, name and version, and the Privacy Notice. Opened
 * from the About row in Settings.
 *
 * An Activity rather than a Fragment pushed over the Home host, for the
 * reason Changelog is: the host's Back always returns to a tab, and Back
 * from here has to return to Settings. It sits outside the host's gates,
 * which is fine for a page that shows nothing about the user.
 *
 * View ids: ib_about_back, tv_about_title, tv_about_app_name,
 * tv_about_version, tv_about_tagline, tv_about_privacy_notice.
 *
 * Flow: Settings → AboutActivity → (Back) Settings
 */
class AboutActivity : AppCompatActivity() {

    private val viewModel: AboutViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        findViewById<ImageButton>(R.id.ib_about_back).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        findViewById<TextView>(R.id.tv_about_version).text =
            getString(R.string.label_about_version, BuildConfig.VERSION_NAME)

        findViewById<TextView>(R.id.tv_about_privacy_notice)
            .setOnClickListener { viewModel.onPrivacyNoticeClicked() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect(::handleEvent)
            }
        }
    }

    private fun handleEvent(event: AboutEvent) {
        when (event) {
            is AboutEvent.ShowMessage ->
                Toast.makeText(this, event.text, Toast.LENGTH_SHORT).show()

            is AboutEvent.OpenUrl -> openUrl(event.url)
        }
    }

    /**
     * Opens a hosted page in whatever browser the phone has.
     *
     * A plain `ACTION_VIEW`, so it needs no permission and no extra
     * dependency. A phone with nothing registered for `https` throws
     * [ActivityNotFoundException], which goes back through the ViewModel so
     * the wording stays with every other user-facing message.
     */
    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            viewModel.onBrowserUnavailable()
        }
    }
}
