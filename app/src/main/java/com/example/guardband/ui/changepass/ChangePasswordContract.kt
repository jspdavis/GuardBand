package com.example.guardband.ui.changepass

import com.example.guardband.base.BaseView
import com.example.guardband.data.model.ValidationResult

interface ChangePasswordContract {

    interface View : BaseView {
        fun navigateToDashboard()
        fun showSuccess(message: String)
        fun showCurrentPasswordError(message: String?)
        fun showNewPasswordError(message: String?)
        fun showConfirmPasswordError(message: String?)
        /**
         * Updates the live password criteria tags based on the current validation state.
         * [hasTyped] is false until the user starts typing, keeping tags neutral.
         */
        fun updatePasswordCriteria(rules: ValidationResult.PasswordRules, hasTyped: Boolean)
    }

    interface Presenter {
        fun onChangePasswordClicked(
            currentPassword: String,
            newPassword: String,
            confirmPassword: String
        )
        fun onNewPasswordTyped(newPassword: String, confirmPassword: String)
        fun onCancelClicked()
    }
}
