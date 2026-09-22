package com.example.guardband.ui.receiver

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.guardband.R

/**
 * Standalone Activity for testing the Alert Receiver screen
 * Hosts the ReceiverFragment
 */
class ReceiverActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_receiver)

        // Set up toolbar
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.apply {
            title = "Alert Receiver"
            setDisplayHomeAsUpEnabled(true)
        }

        // Load ReceiverFragment if not already loaded
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, ReceiverFragment())
                .commit()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
