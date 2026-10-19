package com.example.guardband.ui.reset

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.example.guardband.R
import com.example.guardband.ui.login.LoginActivity

/**
 * Password reset — the confirmation, shown once `confirmPasswordReset` has
 * succeeded.
 *
 * Opened by [ResetPasswordActivity] with the back stack cleared, so the reset
 * form and the spent link cannot be returned to. Receives nothing: the user
 * signs in with the password they just chose, so no email or credential is
 * handed forward.
 *
 * Deliberately has no ViewModel: a static screen whose only action is
 * navigation, with no state, validation or data access - the same exception
 * as `NotificationsFragment`.
 *
 * View ids: iv_reset_success_icon, tv_reset_success_title,
 * tv_reset_success_subtitle, btn_reset_success_login.
 *
 * Flow: ResetPasswordActivity → ResetSuccessActivity → LoginActivity
 * (back stack cleared). Back does the same as the button: there is nothing
 * behind this screen worth returning to.
 */
class ResetSuccessActivity : AppCompatActivity() {

    private lateinit var btnLogin: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reset_success)

        btnLogin = findViewById(R.id.btn_reset_success_login)

        btnLogin.setOnClickListener { goToLogin() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goToLogin()
        })
    }

    private fun goToLogin() {
        startActivity(
            Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
        finish()
    }
}
