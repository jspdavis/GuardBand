package com.example.guardband.ui.changelog

import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.guardband.R

/**
 * Changelog: the app's progress, one card per milestone, newest first.
 * Opened from the Changelog row in Settings.
 *
 * Deliberately has no ViewModel: the content is [Changelog.entries], a
 * constant, so there is no state, input or data access to hold - the same
 * exception as `ResetSuccessActivity` and `NotificationsFragment`.
 *
 * An Activity rather than another Fragment pushed over the Home host, because
 * the host's Back always returns to a tab; from here Back has to return to
 * Settings, which finishing this Activity does for free. It sits outside the
 * host's auth gate, which is fine for a page that shows nothing about the
 * user.
 *
 * View ids: ib_changelog_back, tv_changelog_title, rv_changelog.
 *
 * Flow: Settings → ChangelogActivity → (Back) Settings
 */
class ChangelogActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_changelog)

        findViewById<ImageButton>(R.id.ib_changelog_back).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        val adapter = ChangelogAdapter()
        findViewById<RecyclerView>(R.id.rv_changelog).apply {
            layoutManager = LinearLayoutManager(this@ChangelogActivity)
            this.adapter = adapter
        }
        adapter.submitList(Changelog.entries)
    }
}
