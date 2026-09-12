package com.example.guardband.ui.dashboard

import com.example.guardband.base.BaseView
import com.example.guardband.data.model.EmergencyContact

interface ContactsContract {

    interface View : BaseView {
        fun displayContacts(contacts: List<EmergencyContact>)
        fun showEmptyState()
    }

    interface Presenter {
        fun loadContacts()
        fun onDeleteContactClicked(contactId: String)
    }
}
