package com.example.guardband.ui.signup

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.example.guardband.R
import com.example.guardband.ui.loading.LoadingActivity

/**
 * Welcome — shown once, right after an account is created.
 *
 * **Once, with no stored flag.** The only way here is
 * [SignUpConsentActivity]'s success, which happens exactly once per account:
 * after an email sign-up, or after a first-time Google sign-in finishes its
 * profile. Login and Splash go straight to Home, so a returning user never
 * passes through this screen. Nothing records that it was seen, because there
 * is no route back to it.
 *
 * Opened with the back stack cleared, so the wizard behind it is gone; the
 * account already exists. Back does the same as NEXT: the user is signed in,
 * and the only sensible place left to go is Home.
 *
 * Deliberately has no ViewModel: a static screen whose only action is
 * navigation - the same exception as `NotificationsFragment` and
 * `ResetSuccessActivity`.
 *
 * View ids: tv_welcome_title, tv_welcome_subtitle, iv_welcome_illustration,
 * tv_welcome_description, btn_welcome_next.
 *
 * Flow: SignUpConsentActivity → WelcomeActivity → LoadingActivity → HomeActivity
 */
class WelcomeActivity : AppCompatActivity() {

    private lateinit var btnNext: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_welcome)

        btnNext = findViewById(R.id.btn_welcome_next)

        btnNext.setOnClickListener { goHome() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goHome()
        })
    }

    private fun goHome() {
        startActivity(
            Intent(this, LoadingActivity::class.java).apply {
                putExtra(LoadingActivity.EXTRA_DESTINATION, LoadingActivity.DEST_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
        finish()
    }
}
