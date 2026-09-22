package com.example.guardband.ui.dashboard

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.guardband.R

class HomeFragment : Fragment(), HomeContract.View {

    private lateinit var presenter: HomePresenter
    private lateinit var welcomeTextView: TextView
    private lateinit var ledSwitch: SwitchCompat
    private lateinit var ledStatusTextView: TextView
    private lateinit var deviceStatusTextView: TextView
    private lateinit var statusIndicator: View
    private lateinit var ledIcon: ImageView

    private var ignoreNextSwitchChange = false

    companion object {
        private const val TAG = "HomeFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d(TAG, "onCreateView")
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated")

        welcomeTextView = view.findViewById(R.id.tvWelcome)
        ledSwitch = view.findViewById(R.id.switchLed)
        ledStatusTextView = view.findViewById(R.id.tvLedStatus)
        deviceStatusTextView = view.findViewById(R.id.tvDeviceStatus)
        statusIndicator = view.findViewById(R.id.viewStatusIndicator)
        ledIcon = view.findViewById(R.id.ivLedIcon)

        presenter = HomePresenter()
        presenter.setLifecycleOwner(viewLifecycleOwner)
        presenter.attachView(this)
        
        Log.d(TAG, "Loading profile and device status")
        presenter.loadProfile()
        presenter.loadDeviceStatus()

        ledSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (!ignoreNextSwitchChange) {
                Log.d(TAG, "User toggled LED switch to: $isChecked")
                presenter.onLedToggleClicked(isChecked)
            } else {
                Log.d(TAG, "Ignoring programmatic switch change")
                ignoreNextSwitchChange = false
            }
        }
    }

    override fun onDestroyView() {
        Log.d(TAG, "onDestroyView - cleaning up")
        presenter.cleanup()
        presenter.detachView()
        super.onDestroyView()
    }

    // ── HomeContract.View implementation ──────────────────────────────────────

    override fun displayWelcomeMessage(fullName: String) {
        Log.d(TAG, "displayWelcomeMessage: $fullName")
        welcomeTextView.text = "Welcome, $fullName"
    }

    override fun displayDefaultMessage() {
        Log.d(TAG, "displayDefaultMessage")
        welcomeTextView.text = "Welcome"
    }

    override fun displayLedStatus(isOn: Boolean) {
        Log.d(TAG, "displayLedStatus: $isOn")
        
        if (isOn) {
            ledStatusTextView.text = "LED is ON"
            ledStatusTextView.setTextColor(ContextCompat.getColor(requireContext(), R.color.brand_primary))
            ledIcon.setImageResource(android.R.drawable.presence_online)
            ledIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.brand_primary))
        } else {
            ledStatusTextView.text = "LED is OFF"
            ledStatusTextView.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.darker_gray))
            ledIcon.setImageResource(android.R.drawable.presence_offline)
            ledIcon.setColorFilter(ContextCompat.getColor(requireContext(), android.R.color.darker_gray))
        }
        
        // Update switch without triggering listener
        if (ledSwitch.isChecked != isOn) {
            ignoreNextSwitchChange = true
            ledSwitch.isChecked = isOn
        }
    }

    override fun displayDeviceOnline(isOnline: Boolean) {
        Log.d(TAG, "displayDeviceOnline: $isOnline")
        
        if (isOnline) {
            deviceStatusTextView.text = "Online"
            deviceStatusTextView.setTextColor(ContextCompat.getColor(requireContext(), R.color.brand_primary))
            statusIndicator.setBackgroundTintList(
                ContextCompat.getColorStateList(requireContext(), R.color.brand_primary)
            )
        } else {
            deviceStatusTextView.text = "Offline"
            deviceStatusTextView.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark))
            statusIndicator.setBackgroundTintList(
                ContextCompat.getColorStateList(requireContext(), android.R.color.holo_red_dark)
            )
        }
        
        // Enable/disable switch based on device status
        ledSwitch.isEnabled = isOnline
        ledSwitch.alpha = if (isOnline) 1.0f else 0.5f
    }

    override fun displayCommandSent(state: Boolean) {
        Log.d(TAG, "displayCommandSent: $state")
        Toast.makeText(
            requireContext(),
            "✓ Command sent: LED ${if (state) "ON" else "OFF"}",
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun showLoading() {
        // No-op for HomeFragment
    }

    override fun hideLoading() {
        // No-op for HomeFragment
    }

    override fun showError(message: String) {
        Log.e(TAG, "showError: $message")
        Toast.makeText(requireContext(), "⚠ Error: $message", Toast.LENGTH_LONG).show()
    }
}
