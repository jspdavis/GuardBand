package com.example.guardband.ui.signup

import com.example.guardband.base.BaseView
import com.example.guardband.data.model.EmergencyContact
import com.example.guardband.data.model.ValidationResult

interface SignUpContract {

    interface View : BaseView {
        fun showStep(step: Int)
        fun updateStepIndicator(step: Int)
        fun renderContacts(contacts: List<EmergencyContact>)
        fun clearContactForm()
        fun navigateToLoading()
        fun showFieldError(field: String, message: String?)
        /**
         * Called by the presenter whenever the password field changes in Step 1.
         * The View (via the name fragment) should update its live criteria tags.
         */
        fun updatePasswordCriteria(rules: ValidationResult.PasswordRules, hasTyped: Boolean)
    }

    interface Presenter {
        fun onNameContinue(firstName: String, lastName: String, email: String, password: String)
        fun onLocationContinue(location: String)
        fun onAddContact(name: String, phone: String, relationship: String)
        fun onDeleteContact(contactId: String)
        fun onContactsContinue()
        fun onSkip()
        fun onBack()
        fun getFirstName(): String
        fun getLastName(): String
        fun getCurrentStep(): Int
        /** Called on every keystroke in the password field of Step 1. */
        fun onPasswordTyped(password: String)
    }
}
