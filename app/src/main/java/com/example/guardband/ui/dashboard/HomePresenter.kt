package com.example.guardband.ui.dashboard

import android.util.Log
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.guardband.base.BasePresenter
import com.example.guardband.data.repository.AuthRepository
import com.example.guardband.data.repository.DeviceRepository
import com.example.guardband.utils.DeviceIds
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class HomePresenter(
    private val authRepository: AuthRepository = AuthRepository.getInstance(),
    private val deviceRepository: DeviceRepository = DeviceRepository()
) : BasePresenter<HomeContract.View>(), HomeContract.Presenter {

    private var ledStatusJob: Job? = null
    private var deviceOnlineJob: Job? = null
    private var lifecycleOwner: LifecycleOwner? = null

    companion object {
        private const val TAG = "HomePresenter"
    }

    init {
        Log.d(TAG, "HomePresenter initialized")
    }

    override fun loadProfile() {
        Log.d(TAG, "Loading profile...")
        authRepository.fetchProfile(
            onSuccess = { user ->
                val name = user.fullName.takeIf { it.isNotBlank() }
                if (name != null) {
                    Log.d(TAG, "Profile loaded: $name")
                    view?.displayWelcomeMessage(name)
                } else {
                    Log.d(TAG, "Profile loaded but name is blank")
                    view?.displayDefaultMessage()
                }
            },
            onError = { error ->
                Log.e(TAG, "Failed to load profile: $error")
                view?.displayDefaultMessage()
            }
        )
    }

    override fun loadDeviceStatus() {
        val owner = lifecycleOwner
        if (owner == null) {
            Log.e(TAG, "Cannot load device status: lifecycleOwner is null!")
            return
        }

        Log.d(TAG, "Starting device status observers for device: ${DeviceIds.DEFAULT_DEVICE}")

        // Observe LED status
        ledStatusJob = owner.lifecycleScope.launch {
            Log.d(TAG, "LED status coroutine started")
            try {
                deviceRepository.observeLedStatus(DeviceIds.DEFAULT_DEVICE).collect { isOn ->
                    Log.d(TAG, "LED status update received: $isOn")
                    try {
                        if (isOn != null) {
                            view?.displayLedStatus(isOn)
                        } else {
                            Log.w(TAG, "LED status is null, displaying false")
                            view?.displayLedStatus(false)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error displaying LED status", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in LED status flow", e)
            }
        }

        // Observe device online status
        deviceOnlineJob = owner.lifecycleScope.launch {
            Log.d(TAG, "Device online coroutine started")
            try {
                deviceRepository.observeDeviceOnline(DeviceIds.DEFAULT_DEVICE).collect { isOnline ->
                    Log.d(TAG, "Device online status update received: $isOnline")
                    try {
                        view?.displayDeviceOnline(isOnline)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error displaying device online status", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in device online flow", e)
            }
        }

        Log.d(TAG, "Device status observers launched")
    }

    override fun onLedToggleClicked(desiredState: Boolean) {
        Log.d(TAG, "LED toggle clicked: $desiredState")
        
        deviceRepository.sendLedCommand(
            deviceId = DeviceIds.DEFAULT_DEVICE,
            state = desiredState,
            onSuccess = {
                Log.d(TAG, "LED command sent successfully")
                view?.displayCommandSent(desiredState)
            },
            onError = { message ->
                Log.e(TAG, "LED command failed: $message")
                view?.showError(message)
                // Revert the switch to previous state on error
                view?.displayLedStatus(!desiredState)
            }
        )
    }

    /**
     * Store lifecycle owner for coroutine scope access.
     * Call this before loadDeviceStatus().
     */
    fun setLifecycleOwner(owner: LifecycleOwner) {
        Log.d(TAG, "Lifecycle owner set")
        this.lifecycleOwner = owner
    }

    /**
     * Cleanup method to cancel coroutines.
     * Call before detachView().
     */
    fun cleanup() {
        Log.d(TAG, "Cleaning up - cancelling jobs")
        ledStatusJob?.cancel()
        deviceOnlineJob?.cancel()
        ledStatusJob = null
        deviceOnlineJob = null
        lifecycleOwner = null
        Log.d(TAG, "Cleanup complete")
    }
}
