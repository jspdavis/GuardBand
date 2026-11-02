package com.example.guardband.ui.notifications

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.guardband.R

/**
 * Sends an emergency SMS containing a location link.
 *
 * **Parked until device pairing ships: nothing calls it, and the release
 * manifest does not declare `SEND_SMS`, so it cannot send.** With one shared,
 * world-writable band, any panic from anyone would text real contacts.
 *
 * Before it is called again: pairing, James's approval of `SEND_SMS`, a runtime
 * permission prompt, a guard so a Mock Sender panic cannot text real contacts,
 * and the Privacy Notice and `CONSENT_VERSION` in the same commit. It also needs
 * a real link (the host below is not this project's, and `/track` does not
 * exist), a decision on who gets texted, wording for a panic with no fix, and
 * handling for a failed send. See `docs/team-checklist.md`.
 */
class SmsPanicSender(private val context: Context) {

    /**
     * Sends an emergency SMS with live tracking information to [toPhone].
     *
     * @param toPhone The recipient guardian's phone number.
     * @param lat The latitude of the panic incident.
     * @param lng The longitude of the panic incident.
     * @param sessionId The live tracking session identifier.
     */
    fun sendPanicSms(toPhone: String, lat: Double, lng: Double, sessionId: String): Result<Unit> {
        if (toPhone.isBlank()) {
            return Result.failure(IllegalArgumentException("Recipient phone number cannot be blank."))
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "SEND_SMS permission not granted; skipping SMS dispatch.")
            return Result.failure(SecurityException("SEND_SMS permission is not granted."))
        }

        return try {
            val trackingUrl = "https://guardband.web.app/track/$sessionId"
            val message = context.getString(R.string.sms_panic_body, trackingUrl, lat, lng)
            
            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
                    ?: @Suppress("DEPRECATION") SmsManager.getDefault()
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            val parts = smsManager.divideMessage(message)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(toPhone, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(toPhone, null, message, null, null)
            }
            Log.i(TAG, "Panic SMS successfully dispatched to guardian.")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dispatch panic SMS", e)
            Result.failure(e)
        }
    }

    private companion object {
        const val TAG = "SmsPanicSender"
    }
}
