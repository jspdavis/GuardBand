package com.example.guardband.ui.signup

import android.app.Activity
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.example.guardband.R

/**
 * Binds `view_signup_step_header.xml` for one sign-up step.
 *
 * The header is an `<include>`, and an include cannot vary its children per
 * use site, so which progress bar is filled is set here instead of in each
 * layout. That also means a step can be reordered by changing one argument.
 *
 * View-layer only: it touches Views and nothing else, so it stays out of the
 * ViewModels.
 */
internal object SignUpStepHeader {

    /** Steps in the indicator. Consent sits outside it and has no header. */
    const val TOTAL_STEPS = 3

    /**
     * Fills the first [step] bars, writes "Step n/3", and points the back
     * arrow at the dispatcher so it behaves exactly like the system Back.
     *
     * @param step 1-based position in the indicator.
     */
    fun bind(activity: ComponentActivity, step: Int) {
        require(step in 1..TOTAL_STEPS) { "step out of range" }

        activity.findViewById<TextView>(R.id.tv_signup_step_label).text =
            activity.getString(R.string.label_signup_step, step, TOTAL_STEPS)

        bars(activity).forEachIndexed { index, bar ->
            bar.setBackgroundResource(
                if (index < step) R.drawable.bg_progress_active
                else R.drawable.bg_progress_inactive
            )
        }

        activity.findViewById<ImageButton>(R.id.btn_signup_back).setOnClickListener {
            activity.onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun bars(activity: Activity): List<View> = listOf(
        activity.findViewById(R.id.progress_step_one),
        activity.findViewById(R.id.progress_step_two),
        activity.findViewById(R.id.progress_step_three)
    )
}
