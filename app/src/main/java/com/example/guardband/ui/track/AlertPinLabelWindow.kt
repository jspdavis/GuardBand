package com.example.guardband.ui.track

import android.widget.TextView
import com.example.guardband.R
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.infowindow.MarkerInfoWindow

/**
 * The small label over a tapped alert pin ("Panic, 5 min ago"),
 * `view_alert_pin_label.xml`, opened on whichever pin [Marker.showInfoWindow]
 * is called for. One instance serves every pin on a map: osmdroid's
 * [InfoWindow.open][org.osmdroid.views.overlay.infowindow.InfoWindow.open]
 * closes it before opening it again, so at most one label is ever shown.
 *
 * **It only shows what the state says.** osmdroid's own windows close
 * themselves on a tap; this one hands the tap to [onTap] instead, which goes
 * through [TrackViewModel.onMapTapped], so the view can never be closed
 * behind the state's back and then reopened by the next tick.
 *
 * The text is the marker's title, set by the Fragment just before opening;
 * the window has no `bubble_*` views, so [MarkerInfoWindow]'s own `onOpen` is
 * replaced rather than called.
 */
class AlertPinLabelWindow(
    mapView: MapView,
    onTap: () -> Unit
) : MarkerInfoWindow(R.layout.view_alert_pin_label, mapView) {

    init {
        mView.setOnTouchListener(null)
        mView.setOnClickListener { onTap() }
    }

    override fun onOpen(item: Any?) {
        val marker = item as? Marker
        mMarkerRef = marker
        mView?.findViewById<TextView>(R.id.tv_alert_pin_label)?.text = marker?.title
    }

    override fun onClose() {
        mMarkerRef = null
    }
}
