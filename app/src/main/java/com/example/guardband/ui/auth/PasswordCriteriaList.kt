package com.example.guardband.ui.auth

import android.content.res.ColorStateList
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.guardband.R
import com.example.guardband.utils.PasswordCriteria

/**
 * Binds `view_password_criteria.xml` to a [PasswordCriteria].
 *
 * The same shape as
 * [SignUpStepHeader][com.example.guardband.ui.signup.SignUpStepHeader]: an
 * object that binds an `<include>`, because an include cannot vary its
 * children per use site and the tinting has to live somewhere that is not four
 * copies of a layout.
 *
 * View-layer only — it touches Views and a Context and nothing else, so it
 * stays out of the ViewModels. The rules themselves are
 * [PasswordCriteria]'s, which takes them from `InputValidator`; nothing here
 * decides whether a password is acceptable.
 *
 * Three colours, not two. An unmet rule is **neutral** while the fields are
 * still empty and a **failure** afterwards, so the list does not greet the
 * user by marking a password they have not typed yet as wrong.
 */
internal object PasswordCriteriaList {

    /**
     * Tints each row for [criteria]. Safe to call on every keystroke — it
     * writes a colour and nothing else.
     *
     * @param root any ancestor of the included layout, usually the Activity's
     *   content view.
     */
    fun render(root: View, criteria: PasswordCriteria) {
        tint(root.findViewById(R.id.tv_password_rule_length), criteria.longEnough, criteria.untouched)
        tint(root.findViewById(R.id.tv_password_rule_match), criteria.matches, criteria.untouched)
    }

    private fun tint(row: TextView, met: Boolean, untouched: Boolean) {
        val colorRes = when {
            met -> R.color.rule_met
            untouched -> R.color.rule_neutral
            else -> R.color.rule_unmet
        }
        val color = ContextCompat.getColor(row.context, colorRes)
        row.setTextColor(color)
        // The tick is one shared drawable, recoloured per row rather than
        // swapped for a second asset.
        row.compoundDrawableTintList = ColorStateList.valueOf(color)
    }
}
