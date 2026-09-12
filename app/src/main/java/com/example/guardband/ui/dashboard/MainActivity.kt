package com.example.guardband.ui.dashboard

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.guardband.R
import com.example.guardband.ui.login.LoginActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

/**
 * Post-auth shell hosting Home / Search / Contacts / History / Profile.
 */
class MainActivity : AppCompatActivity(), MainContract.View {

    private lateinit var presenter: MainPresenter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        presenter = MainPresenter()
        presenter.attachView(this)
        presenter.checkLoginStatus()

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        if (savedInstanceState == null) {
            openFragment(HomeFragment())
            bottomNav.selectedItemId = R.id.nav_home
        }

        bottomNav.setOnItemSelectedListener { item ->
            val fragment: Fragment = when (item.itemId) {
                R.id.nav_search -> SearchFragment()
                R.id.nav_contacts -> ContactsFragment()
                R.id.nav_history -> HistoryFragment()
                R.id.nav_profile -> ProfileFragment()
                else -> HomeFragment()
            }
            openFragment(fragment)
            true
        }
    }

    override fun onDestroy() {
        presenter.detachView()
        super.onDestroy()
    }

    private fun openFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    fun logout() {
        presenter.onLogoutClicked()
    }

    // ── MainContract.View implementation ──────────────────────────────────────

    override fun navigateToLogin() {
        startActivity(
            Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
    }

    override fun showLoading() {
        // No-op for MainActivity
    }

    override fun hideLoading() {
        // No-op for MainActivity
    }

    override fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
