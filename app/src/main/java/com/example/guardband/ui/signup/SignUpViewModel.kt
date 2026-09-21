package com.example.guardband.ui.signup

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.guardband.data.model.EmergencyContact
import com.example.guardband.data.model.ValidationResult
import com.example.guardband.data.repository.AuthRepository
import com.example.guardband.data.repository.ContactRepository
import com.example.guardband.data.repository.addContactSuspend
import com.example.guardband.data.repository.deleteContactSuspend
import com.example.guardband.data.repository.registerWithEmailSuspend
import com.example.guardband.data.repository.updateProfileSuspend
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SignUpViewModel(
    private val authRepository: AuthRepository = AuthRepository.getInstance(),
    private val contactRepository: ContactRepository = ContactRepository.getInstance()
) : ViewModel() {

    private val _uiState = MutableStateFlow<SignUpUiState>(SignUpUiState.Idle)
    val uiState: StateFlow<SignUpUiState> = _uiState.asStateFlow()

    // Wizard state
    private var step = 1
    private var firstName = ""
    private var lastName = ""
    private var email = ""
    private var location = ""
    private val contacts = mutableListOf<EmergencyContact>()
    private var accountCreated = false

    fun getCurrentStep() = step
    fun getFirstName() = firstName
    fun getLastName() = lastName

    fun seedFromGoogle(first: String, last: String, mail: String, startStep: Int) {
        firstName = first
        lastName = last
        email = mail
        accountCreated = authRepository.isLoggedIn()
        step = startStep.coerceIn(1, 3)
    }

    // ── Step 1 ────────────────────────────────────────────────────────────────

    fun onPasswordTyped(password: String) {
        val result = ValidationResult.evaluatePassword(password, password)
        _uiState.value = SignUpUiState.PasswordCriteria(result.passwordRules, password.isNotEmpty())
    }

    fun onNameContinue(firstName: String, lastName: String, email: String, password: String) {
        var hasError = false

        if (firstName.isBlank()) {
            _uiState.value = SignUpUiState.FieldError("firstName", "This field is required.")
            hasError = true
        } else {
            _uiState.value = SignUpUiState.FieldError("firstName", null)
        }

        if (lastName.isBlank()) {
            _uiState.value = SignUpUiState.FieldError("lastName", "This field is required.")
            hasError = true
        } else {
            _uiState.value = SignUpUiState.FieldError("lastName", null)
        }

        val trimmedEmail = email.trim()
        val phoneOk = Regex("^\\+?\\d{10,15}$").matches(trimmedEmail)
        val emailOk = Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()
        when {
            trimmedEmail.isBlank() -> {
                _uiState.value = SignUpUiState.FieldError("email", "This field is required.")
                hasError = true
            }
            !phoneOk && !emailOk -> {
                _uiState.value = SignUpUiState.FieldError("email", "Enter a valid email or phone number.")
                hasError = true
            }
            else -> _uiState.value = SignUpUiState.FieldError("email", null)
        }

        val passwordValidation = ValidationResult.evaluatePassword(password, password)
        when {
            password.isBlank() -> {
                _uiState.value = SignUpUiState.FieldError("password", "This field is required.")
                hasError = true
            }
            !passwordValidation.passwordRules.minLength -> {
                _uiState.value = SignUpUiState.FieldError("password", "Password must be at least 8 characters.")
                hasError = true
            }
            !passwordValidation.passwordRules.hasUppercase -> {
                _uiState.value = SignUpUiState.FieldError("password", "Password must contain at least 1 uppercase letter.")
                hasError = true
            }
            !passwordValidation.passwordRules.hasLowercase -> {
                _uiState.value = SignUpUiState.FieldError("password", "Password must contain at least 1 lowercase letter.")
                hasError = true
            }
            !passwordValidation.passwordRules.hasNumberOrSpecial -> {
                _uiState.value = SignUpUiState.FieldError("password", "Password must contain at least 1 number or special character.")
                hasError = true
            }
            else -> _uiState.value = SignUpUiState.FieldError("password", null)
        }

        if (hasError) return

        this.firstName = firstName.trim()
        this.lastName = lastName.trim()

        viewModelScope.launch {
            _uiState.value = SignUpUiState.Loading
            try {
                authRepository.registerWithEmailSuspend(
                    email = trimmedEmail,
                    password = password,
                    firstName = this@SignUpViewModel.firstName,
                    lastName = this@SignUpViewModel.lastName
                )
                accountCreated = true
                this@SignUpViewModel.email = trimmedEmail
                goToStep(2)
            } catch (e: Exception) {
                _uiState.value = SignUpUiState.Error(e.message ?: "Registration failed.")
            }
        }
    }

    // ── Step 2 ────────────────────────────────────────────────────────────────

    fun onLocationContinue(location: String) {
        if (location.isBlank()) {
            _uiState.value = SignUpUiState.FieldError("location", "This field is required.")
            return
        }
        _uiState.value = SignUpUiState.FieldError("location", null)
        this.location = location.trim()

        if (authRepository.isLoggedIn()) {
            viewModelScope.launch {
                _uiState.value = SignUpUiState.Loading
                try {
                    authRepository.updateProfileSuspend(
                        mapOf(
                            "location" to this@SignUpViewModel.location,
                            "firstName" to firstName,
                            "lastName" to lastName
                        )
                    )
                } catch (_: Exception) {
                    // Non-fatal — continue wizard
                } finally {
                    goToStep(3)
                }
            }
        } else {
            goToStep(3)
        }
    }

    // ── Step 3 ────────────────────────────────────────────────────────────────

    fun onAddContact(name: String, phone: String, relationship: String) {
        var hasError = false
        if (name.isBlank()) {
            _uiState.value = SignUpUiState.FieldError("contactName", "This field is required.")
            hasError = true
        } else {
            _uiState.value = SignUpUiState.FieldError("contactName", null)
        }
        if (phone.isBlank()) {
            _uiState.value = SignUpUiState.FieldError("contactPhone", "This field is required.")
            hasError = true
        } else {
            _uiState.value = SignUpUiState.FieldError("contactPhone", null)
        }
        if (relationship.isBlank()) {
            _uiState.value = SignUpUiState.FieldError("relationship", "Please select a relationship.")
            hasError = true
        }
        if (hasError) return

        val contact = EmergencyContact(
            name = name.trim(),
            phoneNumber = phone.trim(),
            relationship = relationship.trim()
        )

        if (authRepository.isLoggedIn()) {
            viewModelScope.launch {
                _uiState.value = SignUpUiState.Loading
                try {
                    val saved = contactRepository.addContactSuspend(contact)
                    contacts.add(saved)
                } catch (_: Exception) {
                    contacts.add(contact.copy(id = "local-${System.currentTimeMillis()}"))
                } finally {
                    _uiState.value = SignUpUiState.ContactsUpdated(contacts.toList())
                }
            }
        } else {
            contacts.add(contact.copy(id = "local-${System.currentTimeMillis()}"))
            _uiState.value = SignUpUiState.ContactsUpdated(contacts.toList())
        }
    }

    fun onDeleteContact(contactId: String) {
        contacts.removeAll { it.id == contactId }
        if (authRepository.isLoggedIn() && !contactId.startsWith("local-")) {
            viewModelScope.launch {
                try { contactRepository.deleteContactSuspend(contactId) } catch (_: Exception) {}
            }
        }
        _uiState.value = SignUpUiState.ContactsUpdated(contacts.toList())
    }

    fun onContactsContinue() {
        if (contacts.isEmpty()) {
            _uiState.value = SignUpUiState.Error("Please add at least 1 emergency contact to continue.")
            return
        }
        if (authRepository.isLoggedIn()) {
            viewModelScope.launch {
                _uiState.value = SignUpUiState.Loading
                try {
                    authRepository.updateProfileSuspend(
                        mapOf(
                            "location" to location,
                            "firstName" to firstName,
                            "lastName" to lastName,
                            "profileComplete" to true
                        )
                    )
                } catch (_: Exception) {
                    // Non-fatal
                } finally {
                    _uiState.value = SignUpUiState.NavigateToLoading
                }
            }
        } else {
            _uiState.value = SignUpUiState.NavigateToLoading
        }
    }

    fun onSkip() {
        when (step) {
            1 -> goToStep(2)
            2 -> goToStep(3)
            else -> onContactsContinue()
        }
    }

    fun onBack() {
        if (step > 1) goToStep(step - 1)
    }

    private fun goToStep(target: Int) {
        step = target
        _uiState.value = SignUpUiState.NavigateToStep(step)
        if (step == 3) {
            _uiState.value = SignUpUiState.ContactsUpdated(contacts.toList())
        }
    }

    class Factory(
        private val authRepository: AuthRepository = AuthRepository.getInstance(),
        private val contactRepository: ContactRepository = ContactRepository.getInstance()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SignUpViewModel(authRepository, contactRepository) as T
    }
}
