package com.example.guardband.data.repository

import com.example.guardband.data.model.EmergencyContact
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

suspend fun ContactRepository.getContactsSuspend(): List<EmergencyContact> =
    suspendCancellableCoroutine { cont ->
        getContacts(
            onSuccess = { list -> if (cont.isActive) cont.resume(list) },
            onError = { msg -> if (cont.isActive) cont.resumeWithException(Exception(msg)) }
        )
    }

suspend fun ContactRepository.addContactSuspend(contact: EmergencyContact): EmergencyContact =
    suspendCancellableCoroutine { cont ->
        addContact(
            contact = contact,
            onSuccess = { saved -> if (cont.isActive) cont.resume(saved) },
            onError = { msg -> if (cont.isActive) cont.resumeWithException(Exception(msg)) }
        )
    }

suspend fun ContactRepository.deleteContactSuspend(contactId: String): Unit =
    suspendCancellableCoroutine { cont ->
        deleteContact(
            contactId = contactId,
            onSuccess = { if (cont.isActive) cont.resume(Unit) },
            onError = { msg -> if (cont.isActive) cont.resumeWithException(Exception(msg)) }
        )
    }
