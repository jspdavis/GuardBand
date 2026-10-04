package com.example.guardband.ui.notifications

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import androidx.fragment.app.Fragment
import com.example.guardband.R

/**
 * Notifications inbox, pushed inside the Home host from Track's bell (the
 * bottom bar stays visible with no tab selected).
 *
 * Deliberately has no ViewModel, like ForgotSuccessActivity: a static
 * placeholder whose only action is Back. Give it one when the inbox gets data.
 */
class NotificationsFragment : Fragment(R.layout.fragment_notifications) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<ImageButton>(R.id.ib_notifications_back).setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }
}
