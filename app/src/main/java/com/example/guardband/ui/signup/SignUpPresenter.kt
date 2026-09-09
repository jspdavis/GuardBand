package com.example.guardband.ui.signup

import android.util.Patterns
import com.example.guardband.base.BasePresenter
import com.example.guardband.data.model.EmergencyContact
import com.example.guardband.data.model.ValidationResult
import com.example.guardband.data.repository.AuthRepository
import com.example.guardband.data.repository.ContactRepository

class SignUpPresenter(
    private val authRepository: AuthRepository = AuthRepository.getInstance(),
    private val contactRepository: ContactRepository = ContactRepository.getInstance()
) : BasePresenter<SignUpContract.View>(), SignUpContract.Presenter {

    private var step = 1
    private var firstName = ""
    private var lastName = ""
    private var email = ""
    private var location = ""
    private val contacts = mutableListOf<EmergencyContact>()
    private var accountCreated = false

    fun seedFromGoogle(first: String, last: String, mail: String, startStep: Int) {
        firstName = first
        lastName = last
        email = mail
        accountCreated = authRepository.isLoggedIn()
        step = startStep.coerceIn(1, 3)
    }

    override fun getFirstName() = firstName
    override fun getLastName() = lastName
    override fun getCurrentStep() = step

    // ── Step 1: Name & Credentials ───────────────────────────────────────────

    override fun onPasswordTyped(password: String) {
        val result = ValidationResult.evaluatePassword(password, password)
        view?.updatePasswordCriteria(result.passwordRules, hasTyped = password.isNotEmpty())
    }

    override fun onNameContinue(
        firstName: String,
        lastName: String,
        email: String,
        password: String
    ) {
        var hasError = false

        // First Name — required
        if (firstName.isBlank()) {
            view?.showFieldError("firstName", "This field is required.")
            hasError = true
        } else {
            view?.showFieldError("firstName", null)
        }

        // Last Name — required
        if (lastName.isBlank()) {
            view?.showFieldError("lastName", "This field is required.")
            hasError = true
        } else {
            view?.showFieldError("lastName", null)
        }

        // Email — required + format check
        val trimmedEmail = email.trim()
        if (trimmedEmail.isBlank()) {
            view?.showFieldError("email", "This field is required.")
            hasError = true
        } else if (!Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()) {
            view?.showFieldError("email", "Enter a valid email address.")
            hasError = true
        } else {
            view?.showFieldError("email", null)
        }

        // Password — required + all 4 criteria
        val passwordValidation = ValidationResult.evaluatePassword(password, password)
        when {
            password.isBlank() -> {
                view?.showFieldError("password", "This field is required.")
                hasError = true
            }
            !passwordValidation.passwordRules.minLength -> {
                view?.showFieldError("password", "Password must be at least 8 characters.")
                hasError = true
            }
            !passwordValidation.passwordRules.hasUppercase -> {
                view?.showFieldError("password", "Password must contain at least 1 uppercase letter.")
                hasError = true
            }
            !passwordValidation.passwordRules.hasLowercase -> {
                view?.showFieldError("password", "Password must contain at least 1 lowercase letter.")
                hasError = true
            }
            !passwordValidation.passwordRules.hasNumberOrSpecial -> {
                view?.showFieldError("password", "Password must contain at least 1 number or special character.")
                hasError = true
            }
            else -> {
                view?.showFieldError("password", null)
            }
        }

        if (hasError) return

        this.firstName = firstName.trim()
        this.lastName = lastName.trim()

        view?.showLoading()
        authRepository.registerWithEmail(
            email = trimmedEmail,
            password = password,
            firstName = this.firstName,
            lastName = this.lastName,
            onSuccess = {
                accountCreated = true
                this.email = trimmedEmail
                view?.hideLoading()
                goToStep(2)
            },
            onError = { msg ->
                view?.hideLoading()
                view?.showError(msg)
            }
        )
    }

    // ── Step 2: Location ─────────────────────────────────────────────────────

    override fun onLocationContinue(location: String) {
        if (location.isBlank()) {
            view?.showFieldError("location", "This field is required.")
            return
        }
        view?.showFieldError("location", null)
        this.location = location.trim()

        if (authRepository.isLoggedIn()) {
            view?.showLoading()
            authRepository.updateProfile(
                mapOf(
                    "location" to this.location,
                    "firstName" to firstName,
                    "lastName" to lastName
                ),
                onSuccess = {
                    view?.hideLoading()
                    goToStep(3)
                },
                onError = { msg ->
                    view?.hideLoading()
                    // Non-fatal: navigate anyway so wizard is not stuck.
                    view?.showError(msg)
                    goToStep(3)
                }
            )
        } else {
            goToStep(3)
        }
    }

    // ── Step 3: Emergency Contacts ───────────────────────────────────────────

    override fun onAddContact(name: String, phone: String, relationship: String) {
        var hasError = false

        if (name.isBlank()) {
            view?.showFieldError("contactName", "This field is required.")
            hasError = true
        } else {
            view?.showFieldError("contactName", null)
        }

        if (phone.isBlank()) {
            view?.showFieldError("contactPhone", "This field is required.")
            hasError = true
        } else {
            view?.showFieldError("contactPhone", null)
        }

        if (relationship.isBlank()) {
            view?.showFieldError("relationship", "Please select a relationship.")
            hasError = true
        }

        if (hasError) return

        val contact = EmergencyContact(
            name = name.trim(),
            phoneNumber = phone.trim(),
            relationship = relationship.trim()
        )

        if (authRepository.isLoggedIn()) {
            view?.showLoading()
            contactRepository.addContact(
                contact = contact,
                onSuccess = { saved ->
                    contacts.add(saved)
                    view?.hideLoading()
                    view?.clearContactForm()
                    view?.renderContacts(contacts.toList())
                },
                onError = { msg ->
                    view?.hideLoading()
                    // Keep locally so wizard still works if RTDB write fails temporarily.
                    contacts.add(contact.copy(id = "local-${System.currentTimeMillis()}"))
                    view?.clearContactForm()
                    view?.renderContacts(contacts.toList())
                    view?.showError(msg)
                }
            )
        } else {
            contacts.add(contact.copy(id = "local-${System.currentTimeMillis()}"))
            view?.clearContactForm()
            view?.renderContacts(contacts.toList())
        }
    }

    override fun onDeleteContact(contactId: String) {
        contacts.removeAll { it.id == contactId }
        if (authRepository.isLoggedIn() && !contactId.startsWith("local-")) {
            contactRepository.deleteContact(contactId, onSuccess = {}, onError = {})
        }
        view?.renderContacts(contacts.toList())
    }

    override fun onContactsContinue() {
        // At least 1 emergency contact is mandatory before completing registration.
        if (contacts.isEmpty()) {
            view?.showError("Please add at least 1 emergency contact to continue.")
            return
        }

        if (authRepository.isLoggedIn()) {
            view?.showLoading()
            authRepository.updateProfile(
                mapOf(
                    "location" to location,
                    "firstName" to firstName,
                    "lastName" to lastName,
                    "profileComplete" to true
                ),
                onSuccess = {
                    view?.hideLoading()
                    view?.navigateToLoading()
                },
                onError = {
                    view?.hideLoading()
                    view?.navigateToLoading()
                }
            )
        } else {
            view?.navigateToLoading()
        }
    }

    // ── Navigation ───────────────────────────────────────────────────────────

    override fun onSkip() {
        when (step) {
            1 -> goToStep(2)
            2 -> goToStep(3)
            else -> onContactsContinue()
        }
    }

    override fun onBack() {
        if (step > 1) goToStep(step - 1)
    }

    private fun goToStep(target: Int) {
        step = target
        view?.updateStepIndicator(step)
        view?.showStep(step)
        if (step == 3) {
            view?.renderContacts(contacts.toList())
        }
    }
}
