package com.example.guardband.ui.about

import com.example.guardband.testing.MainDispatcherRule
import com.example.guardband.utils.HostedPages
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** About: the Privacy Notice row, which was a Settings row until 2026-10-20. */
class AboutViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val viewModel = AboutViewModel()

    @Test
    fun `the Privacy Notice row opens the hosted notice`() = runTest {
        viewModel.onPrivacyNoticeClicked()

        assertEquals(AboutEvent.OpenUrl(HostedPages.privacyNotice), viewModel.events.first())
    }

    @Test
    fun `a phone with no browser is told so`() = runTest {
        viewModel.onBrowserUnavailable()

        assertEquals(AboutEvent.ShowMessage(AboutViewModel.MSG_NO_BROWSER), viewModel.events.first())
    }
}
