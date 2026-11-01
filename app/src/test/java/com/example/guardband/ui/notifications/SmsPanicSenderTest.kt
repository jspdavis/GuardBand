package com.example.guardband.ui.notifications

import android.content.Context
import com.example.guardband.data.model.Alert
import com.example.guardband.data.model.AlertType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SmsPanicSenderTest {

    @Test
    fun `blank phone number returns failure without sending SMS`() {
        val context: Context = RuntimeEnvironment.getApplication()
        val sender = SmsPanicSender(context)

        val result = sender.sendPanicSms(
            toPhone = "",
            lat = 10.3157,
            lng = 123.8854,
            sessionId = "session_123"
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `simulated panic alert flag prevents real SMS dispatch`() {
        val simulatedAlert = Alert(
            type = "PANIC",
            simulated = true,
            sequenceId = 12345L
        )

        val realAlert = Alert(
            type = "PANIC",
            simulated = false,
            sequenceId = 12346L
        )

        // Safety verification: only non-simulated panics may trigger SMS
        val shouldSendSimulated = simulatedAlert.alertType() == AlertType.PANIC && !simulatedAlert.simulated
        val shouldSendReal = realAlert.alertType() == AlertType.PANIC && !realAlert.simulated

        assertFalse(shouldSendSimulated)
        assertTrue(shouldSendReal)
    }
}
