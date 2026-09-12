package com.example.guardband.ui.dashboard

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.guardband.R
import com.example.guardband.data.model.User
import com.example.guardband.ui.changepass.ChangePasswordActivity
import com.google.android.material.button.MaterialButton

class ProfileFragment : Fragment(), ProfileContract.View {

    private lateinit var presenter: ProfilePresenter
    private lateinit var titleView: TextView
    private lateinit var subtitleView: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_profile, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        titleView = view.findViewById(R.id.tvTitle)
        subtitleView = view.findViewById(R.id.tvSubtitle)

        titleView.text = "Profile"

        view.findViewById<MaterialButton>(R.id.btnLogout).setOnClickListener {
            (activity as? MainActivity)?.logout()
        }

        // Wire up Change Password button if it exists in the layout (optional enhancement)
        view.findViewById<MaterialButton?>(R.id.btnChangePassword)?.setOnClickListener {
            startActivity(Intent(requireContext(), ChangePasswordActivity::class.java))
        }

        presenter = ProfilePresenter()
        presenter.attachView(this)
        presenter.loadProfile()
    }

    override fun onDestroyView() {
        presenter.detachView()
        super.onDestroyView()
    }

    // ── ProfileContract.View implementation ───────────────────────────────────

    override fun displayProfile(user: User) {
        subtitleView.text = "${user.fullName}\n${user.email}\n${user.location}"
    }

    override fun displaySignedInMessage() {
        subtitleView.text = "Signed in"
    }

    override fun showLoading() {
        // No-op for ProfileFragment
    }

    override fun hideLoading() {
        // No-op for ProfileFragment
    }

    override fun showError(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }
}
