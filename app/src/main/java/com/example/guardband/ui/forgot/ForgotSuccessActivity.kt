package com.example.guardband.ui.forgot

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.example.guardband.R
import com.example.guardband.ui.login.LoginActivity

/**
 * Forgot Password — Step 2, the confirmation.
 *
 * Says a reset link was sent, without saying whether the account exists: it is
 * shown for an unknown email too, so the screen cannot be used to find out
 * which addresses are registered. The reset itself happens on Firebase's own
 * hosted page, which is why there is no verify-code or new-password step.
 *
 * Deliberately has no ViewModel: a static screen whose only action is
 * navigation, with no state, validation or data access.
 *
 * View ids: tv_forgot_success_icon, tv_forgot_success_title,
 * tv_forgot_success_subtitle, btn_forgot_success_login.
 *
 * Flow: ForgotSuccessActivity → LoginActivity (clears the forgot back-stack)
 */
class ForgotSuccessActivity : AppCompatActivity() {

    private lateinit var btnBackToLogin: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_success)

        btnBackToLogin = findViewById(R.id.btn_forgot_success_login)

        btnBackToLogin.setOnClickListener {
            startActivity(
                Intent(this, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            )
        }
    }
}
