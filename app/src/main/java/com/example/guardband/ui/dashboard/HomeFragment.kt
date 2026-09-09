package com.example.guardband.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.guardband.R
import com.example.guardband.data.repository.AuthRepository

class HomeFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_simple_page, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<TextView>(R.id.tvTitle).text = "Home"
        AuthRepository.getInstance().fetchProfile(
            onSuccess = { user ->
                view.findViewById<TextView>(R.id.tvSubtitle).text =
                    "Welcome${user.fullName.takeIf { it.isNotBlank() }?.let { ", $it" } ?: ""}.\nYou are protected."
            },
            onError = {
                view.findViewById<TextView>(R.id.tvSubtitle).text = "You are protected."
            }
        )
    }
}
