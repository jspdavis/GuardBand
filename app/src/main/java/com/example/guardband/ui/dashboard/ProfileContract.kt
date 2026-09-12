package com.example.guardband.ui.dashboard

import com.example.guardband.base.BaseView
import com.example.guardband.data.model.User

interface ProfileContract {

    interface View : BaseView {
        fun displayProfile(user: User)
        fun displaySignedInMessage()
    }

    interface Presenter {
        fun loadProfile()
    }
}
