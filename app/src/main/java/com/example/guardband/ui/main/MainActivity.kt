package com.example.guardband.ui.main

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.guardband.R
import com.example.guardband.ui.mocksender.MockSenderActivity
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "GuardBand"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        checkDatabaseConnection()
        setupDebugButton()
    }

    /**
     * Temporary debug button to launch MockSenderActivity.
     * TODO: Remove this once Ishi's auth flow is integrated and replace with proper navigation.
     */
    private fun setupDebugButton() {
        findViewById<Button>(R.id.btnOpenMockSender).setOnClickListener {
            startActivity(Intent(this, MockSenderActivity::class.java))
        }
    }

    private fun checkDatabaseConnection() {
        val database = FirebaseDatabase.getInstance()
        val connectedRef = database.getReference(".info/connected")

        connectedRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) ?: false
                if (connected) {
                    Log.d(TAG, "Connected to Firebase Realtime Database")
                } else {
                    Log.d(TAG, "Disconnected from Firebase Realtime Database")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Database connection listener cancelled: ${error.message}")
            }
        })
    }
}
