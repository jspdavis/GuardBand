package com.example.guardband.ui.dashboard

import com.example.guardband.base.BasePresenter
import com.example.guardband.data.repository.ContactRepository

class ContactsPresenter(
    private val contactRepository: ContactRepository = ContactRepository.getInstance()
) : BasePresenter<ContactsContract.View>(), ContactsContract.Presenter {

    override fun loadContacts() {
        contactRepository.getContacts(
            onSuccess = { list ->
                if (list.isEmpty()) {
                    view?.showEmptyState()
                } else {
                    view?.displayContacts(list)
                }
            },
            onError = { error ->
                view?.showEmptyState()
                view?.showError(error)
            }
        )
    }

    override fun onDeleteContactClicked(contactId: String) {
        view?.showLoading()
        contactRepository.deleteContact(
            contactId = contactId,
            onSuccess = {
                view?.hideLoading()
                loadContacts()
            },
            onError = { error ->
                view?.hideLoading()
                view?.showError(error)
            }
        )
    }
}
