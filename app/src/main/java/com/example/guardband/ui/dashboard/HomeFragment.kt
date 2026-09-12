package com.example.guardband.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.guardband.R

class HomeFragment : Fragment(), HomeContract.View {

    private lateinit var presenter: HomePresenter
    private lateinit var titleView: TextView
    private lateinit var subtitleView: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_simple_page, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        titleView = view.findViewById(R.id.tvTitle)
        subtitleView = view.findViewById(R.id.tvSubtitle)

        titleView.text = "Home"

        presenter = HomePresenter()
        presenter.attachView(this)
        presenter.loadProfile()
    }

    override fun onDestroyView() {
        presenter.detachView()
        super.onDestroyView()
    }

    // ── HomeContract.View implementation ──────────────────────────────────────

    override fun displayWelcomeMessage(fullName: String) {
        subtitleView.text = "Welcome, $fullName.\nYou are protected."
    }

    override fun displayDefaultMessage() {
        subtitleView.text = "You are protected."
    }

    override fun showLoading() {
        // No-op for HomeFragment
    }

    override fun hideLoading() {
        // No-op for HomeFragment
    }

    override fun showError(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }
}
