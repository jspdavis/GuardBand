package com.example.guardband.ui.dashboard

import com.example.guardband.base.BaseView

interface HomeContract {

    interface View : BaseView {
        fun displayWelcomeMessage(fullName: String)
        fun displayDefaultMessage()
        fun displayLedStatus(isOn: Boolean)
        fun displayDeviceOnline(isOnline: Boolean)
        fun displayCommandSent(state: Boolean)
    }

    interface Presenter {
        fun loadProfile()
        fun loadDeviceStatus()
        fun onLedToggleClicked(desiredState: Boolean)
    }
}
