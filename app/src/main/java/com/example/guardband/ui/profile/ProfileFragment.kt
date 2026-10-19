package com.example.guardband.ui.profile

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.guardband.R
import com.example.guardband.ui.avatar.AvatarImage
import com.example.guardband.ui.home.HomeViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Profile tab: avatar, welcome header and email.
 *
 * View ids: ib_profile_settings, ib_profile_notifications, iv_profile_avatar,
 * tv_profile_welcome, tv_profile_email.
 *
 * The avatar is the Google profile photo when [ProfileUiState.avatarUrl] has
 * one, and the default icon otherwise; [AvatarImage] does the loading, the
 * same way as on the Track tab.
 */
class ProfileFragment : Fragment(R.layout.fragment_profile) {

    private lateinit var avatarImage: AvatarImage
    private lateinit var tvWelcome: TextView
    private lateinit var tvEmail: TextView

    private val viewModel: ProfileViewModel by viewModels { ProfileViewModel.Factory }
    private val homeViewModel: HomeViewModel by activityViewModels { HomeViewModel.Factory }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<ImageButton>(R.id.ib_profile_settings)
            .setOnClickListener { homeViewModel.onSettingsClicked() }
        view.findViewById<ImageButton>(R.id.ib_profile_notifications)
            .setOnClickListener { homeViewModel.onNotificationsClicked() }

        avatarImage = AvatarImage(view.findViewById(R.id.iv_profile_avatar))
        tvWelcome  = view.findViewById(R.id.tv_profile_welcome)
        tvEmail    = view.findViewById(R.id.tv_profile_email)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
        // Its own collector, so the record read landing does not re-request
        // the photo: only a different URL reaches the avatar.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.map { it.avatarUrl }.distinctUntilChanged()
                    .collect(avatarImage::show)
            }
        }
    }

    override fun onDestroyView() {
        avatarImage.dispose()
        super.onDestroyView()
    }

    private fun render(state: ProfileUiState) {
        tvWelcome.text = getString(R.string.label_profile_welcome, state.userName)
        tvEmail.text = state.email
    }
}
