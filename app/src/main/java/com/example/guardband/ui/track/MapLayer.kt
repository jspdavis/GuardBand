package com.example.guardband.ui.track

/**
 * The selectable base maps on the Track tab.
 * - [AUTO]: MapTiler's Streets style, darkening with the theme (Streets in light mode,
 *   Streets Dark in dark mode).
 * - [STREET]: MapTiler's Streets style in light mode, regardless of theme.
 * - [SATELLITE]: MapTiler's Satellite imagery style.
 *
 * The choice lives in [TrackUiState.selectedLayer] so it survives a theme
 * change (the Fragment is recreated, and it re-applies the layer). [AUTO]
 * is the default because it is the style matching the system theme.
 */
enum class MapLayer {
    AUTO,
    STREET,
    SATELLITE
}
