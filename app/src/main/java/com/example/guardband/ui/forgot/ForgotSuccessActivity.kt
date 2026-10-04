package com.example.guardband.ui.forgot

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.example.guardband.R
import com.example.guardband.ui.login.LoginActivity

/**
 * Forgot Password — Step 4.
 * Confirms that the password was changed successfully.
 *
 * Deliberately has no ViewModel: a static screen whose only action is
 * navigation, with no state, validation or data access.
 *
 * Flow: ForgotSuccessActivity → LoginActivity (clears forgot back-stack)
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
