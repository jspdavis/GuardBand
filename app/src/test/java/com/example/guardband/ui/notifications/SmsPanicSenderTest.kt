package com.example.guardband.ui.notifications

import android.content.Context
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
}
