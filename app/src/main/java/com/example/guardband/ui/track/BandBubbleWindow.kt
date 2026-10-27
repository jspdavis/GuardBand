package com.example.guardband.ui.track

import android.view.View
import android.widget.TextView
import androidx.core.view.isVisible
import com.example.guardband.R
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.infowindow.MarkerInfoWindow

/**
 * The band's bubble over the face marker, `view_band_bubble.xml`: the
 * marker's title ("Your band") and, while [setIncidentAvailable] says so, a
 * "View incident" action that calls [onViewIncident].
 *
 * **The face behaves as it always has.** The marker keeps osmdroid's own tap
 * handling (open this bubble and pan to the band), and a tap on the bubble
 * closes it, as osmdroid's stock bubble did. Only the action is new, and only
 * while the band's latest non-tracking alert is a panic
 * ([TrackUiState.bandPanic]): the way to a panic whose pin the face hides.
 */
class BandBubbleWindow(
    mapView: MapView,
    onViewIncident: () -> Unit
) : MarkerInfoWindow(R.layout.view_band_bubble, mapView) {

    private val action: View = mView.findViewById(R.id.btn_band_bubble_view_incident)

    init {
        mView.setOnTouchListener(null)
        mView.setOnClickListener { close() }
        action.setOnClickListener {
            close()
            onViewIncident()
        }
    }

    /** Shows or hides "View incident", on the open bubble too. */
    fun setIncidentAvailable(available: Boolean) {
        action.isVisible = available
    }

    override fun onOpen(item: Any?) {
        val marker = item as? Marker
        mMarkerRef = marker
        mView?.findViewById<TextView>(R.id.tv_band_bubble_title)?.text = marker?.title
    }

    override fun onClose() {
        mMarkerRef = null
    }
}
