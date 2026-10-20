package com.example.guardband.testing

import com.example.guardband.ui.display.DisplayPrefs
import com.example.guardband.ui.display.ThemeChoice

/** In-memory [DisplayPrefs], counting writes. */
class FakeDisplayPrefs(
    initial: ThemeChoice = ThemeChoice.DEFAULT
) : DisplayPrefs {

    var writes = 0
        private set

    override var themeChoice: ThemeChoice = initial
        set(value) {
            field = value
            writes++
        }
}
