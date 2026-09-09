package com.example.guardband.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.guardband.R
import com.example.guardband.data.repository.AuthRepository
import com.google.android.material.button.MaterialButton

class ProfileFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_profile, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<TextView>(R.id.tvTitle).text = "Profile"
        AuthRepository.getInstance().fetchProfile(
            onSuccess = { user ->
                view.findViewById<TextView>(R.id.tvSubtitle).text =
                    "${user.fullName}\n${user.email}\n${user.location}"
            },
            onError = {
                view.findViewById<TextView>(R.id.tvSubtitle).text = "Signed in"
            }
        )
        view.findViewById<MaterialButton>(R.id.btnLogout).setOnClickListener {
            (activity as? MainActivity)?.logout()
        }
    }
}
