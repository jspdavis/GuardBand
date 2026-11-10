package com.example.guardband.ui.device

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.guardband.R
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/**
 * Dialog for the two pairing actions:
 * - **Watch someone (I'm a watcher):** enter the wearer's 6-char code.
 * - **Share my code (I'm a wearer):** generate a code and show it.
 *
 * Both delegate to [InviteCodeViewModel]. Nothing here logs a uid or code value.
 */
class InviteCodeDialogFragment : DialogFragment() {

    private val viewModel: InviteCodeViewModel by viewModels { InviteCodeViewModel.Factory }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_invite_code, null)

        val toggleTabs    = view.findViewById<MaterialButtonToggleGroup>(R.id.toggle_invite_tabs)
        val panelEnter    = view.findViewById<View>(R.id.panel_invite_enter)
        val panelShare    = view.findViewById<View>(R.id.panel_invite_share)
        val etCode        = view.findViewById<EditText>(R.id.et_invite_code)
        val btnRedeem     = view.findViewById<Button>(R.id.btn_invite_redeem)
        val btnGenerate   = view.findViewById<Button>(R.id.btn_invite_generate)
        val codeContainer = view.findViewById<View>(R.id.layout_invite_code_container)
        val tvCode        = view.findViewById<TextView>(R.id.tv_invite_generated_code)
        val btnCopy       = view.findViewById<Button>(R.id.btn_invite_copy)
        val tvStatus      = view.findViewById<TextView>(R.id.tv_invite_status)
        val progress      = view.findViewById<ProgressBar>(R.id.progress_invite)

        fun copyCodeToClipboard() {
            val code = tvCode.text?.toString().orEmpty()
            if (code.isNotEmpty()) {
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("GuardBand Code", code)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(requireContext(), R.string.toast_code_copied, Toast.LENGTH_SHORT).show()
            }
        }

        tvCode.setOnClickListener { copyCodeToClipboard() }
        btnCopy.setOnClickListener { copyCodeToClipboard() }

        fun showEnter() {
            panelEnter.isVisible = true
            panelShare.isVisible = false
        }

        fun showShare() {
            panelEnter.isVisible = false
            panelShare.isVisible = true
        }

        showEnter()
        toggleTabs.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btn_invite_tab_enter -> showEnter()
                    R.id.btn_invite_tab_share -> showShare()
                }
            }
        }

        btnRedeem.setOnClickListener {
            viewModel.redeem(etCode.text.toString().trim().uppercase())
        }
        btnGenerate.setOnClickListener { viewModel.generate() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    progress.isVisible = state.isLoading
                    tvStatus.isVisible = state.statusMessage != null
                    tvStatus.text = state.statusMessage
                    val hasCode = state.generatedCode != null
                    codeContainer.isVisible = hasCode
                    if (hasCode) {
                        tvCode.text = state.generatedCode
                    }
                    if (state.redeemed) {
                        Toast.makeText(
                            requireContext(),
                            R.string.toast_connected_success,
                            Toast.LENGTH_LONG
                        ).show()
                        dismiss()
                    }
                }
            }
        }

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.title_invite_dialog)
            .setView(view)
            .setNegativeButton(android.R.string.cancel) { _, _ -> dismiss() }
            .create()
    }

    companion object {
        const val TAG = "InviteCodeDialog"
    }
}
