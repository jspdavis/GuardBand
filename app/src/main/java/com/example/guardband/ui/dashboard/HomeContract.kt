package com.example.guardband.ui.dashboard

import com.example.guardband.base.BaseView
import com.example.guardband.data.model.User

interface HomeContract {

    interface View : BaseView {
        fun displayWelcomeMessage(fullName: String)
        fun displayDefaultMessage()
    }

    interface Presenter {
        fun loadProfile()
    }
}
