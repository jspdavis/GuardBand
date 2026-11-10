package com.example.guardband.testing

import com.example.guardband.data.repository.BandSelectionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeBandSelectionStore(initial: String? = null) : BandSelectionStore {
    private val _selected = MutableStateFlow(initial)
    override val selected: StateFlow<String?> = _selected.asStateFlow()

    override fun select(deviceId: String?) {
        _selected.value = deviceId
    }
}
