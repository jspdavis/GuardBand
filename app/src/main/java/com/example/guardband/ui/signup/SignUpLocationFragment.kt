package com.example.guardband.ui.signup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.guardband.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class SignUpLocationFragment : Fragment() {

    interface Host {
        fun onLocationContinue(location: String)
    }

    private var host: Host? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_signup_location, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        host = activity as? Host
        view.findViewById<MaterialButton>(R.id.btnContinue).setOnClickListener {
            host?.onLocationContinue(
                view.findViewById<TextInputEditText>(R.id.etLocation).text?.toString().orEmpty()
            )
        }
    }

    fun showLocationError(message: String?) {
        view?.findViewById<TextInputLayout>(R.id.tilLocation)?.error = message
    }
}
