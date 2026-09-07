package com.example.guardband.ui.dashboard

import com.example.guardband.base.BasePresenter
import com.example.guardband.data.MockRepository

/**
 * Presenter for [DashboardActivity].
 *
 * Loads mock user data and contacts on attach; handles logout action.
 */
class DashboardPresenter(
    /** Display name passed in from the session after login/register. */
    private val userName: String
) : BasePresenter<DashboardContract.View>(), DashboardContract.Presenter {

    override fun loadDashboard() {
        view?.showLoading()
        view?.showUserName(userName.ifBlank { "User" })

        MockRepository.getContacts { contacts ->
            view?.hideLoading()
            view?.showContacts(contacts)
        }
    }

    override fun onLogoutClicked() {
        // No session tokens to clear in mock mode — simply navigate to Login.
        view?.navigateToLogin()
    }
}
