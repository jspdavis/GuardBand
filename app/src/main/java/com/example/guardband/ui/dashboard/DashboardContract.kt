package com.example.guardband.ui.dashboard

import com.example.guardband.base.BaseView
import com.example.guardband.data.ContactModel

/**
 * MVP contract for the Dashboard screen.
 */
interface DashboardContract {

    interface View : BaseView {
        /** Populate the welcome header with the user's display name. */
        fun showUserName(name: String)

        /** Render the emergency contacts list. */
        fun showContacts(contacts: List<ContactModel>)

        /** Navigate back to Login and clear the entire back-stack. */
        fun navigateToLogin()
    }

    interface Presenter {
        fun loadDashboard()
        fun onLogoutClicked()
    }
}
