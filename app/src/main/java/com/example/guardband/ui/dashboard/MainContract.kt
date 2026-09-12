package com.example.guardband.ui.dashboard

import com.example.guardband.base.BaseView

interface MainContract {

    interface View : BaseView {
        fun navigateToLogin()
    }

    interface Presenter {
        fun checkLoginStatus()
        fun onLogoutClicked()
    }
}
