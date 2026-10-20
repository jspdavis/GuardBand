package com.example.guardband.ui.notifications

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import androidx.fragment.app.Fragment
import com.example.guardband.R

/**
 * Notifications inbox, pushed inside the Home host from a tab's bell (the
 * host hides the bottom bar while it shows).
 *
 * Deliberately has no ViewModel, like the other static navigation-only
 * screens (`ResetSuccessActivity`, `WelcomeActivity`): a placeholder whose
 * only action is Back. Give it one when the inbox gets data.
 *
 * For testing, the inbox shows one static sample row (cv_notifications_welcome)
 * and hides the empty text; both live only in the layout.
 *
 * View ids: ib_notifications_back, tv_notifications_title,
 * cv_notifications_welcome, tv_notifications_welcome_title,
 * tv_notifications_welcome_body, tv_notifications_empty.
 */
class NotificationsFragment : Fragment(R.layout.fragment_notifications) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<ImageButton>(R.id.ib_notifications_back).setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }
}
