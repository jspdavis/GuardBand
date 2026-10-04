package com.example.guardband.data

import com.example.guardband.data.repository.AuthRepository
import com.example.guardband.data.repository.ContactRepository
import com.example.guardband.data.repository.InMemoryAuthRepository
import com.example.guardband.data.repository.InMemoryContactRepository

/**
 * Manual wiring for repositories (no DI framework).
 *
 * Swap the implementations here (e.g. for Firebase-backed ones) without
 * touching ViewModels.
 */
object RepositoryProvider {
    val authRepository: AuthRepository by lazy { InMemoryAuthRepository() }
    val contactRepository: ContactRepository by lazy { InMemoryContactRepository() }
}
