package com.example.guardband.data

/**
 * Device identity until user↔band pairing exists.
 *
 * In the app, only [RepositoryProvider] should read this (it seeds the
 * `FixedBandRepository`); ViewModels never see a device id. The debug mock
 * sender and `InMemoryStore`'s test seed read it too, as the band's side.
 */
object DeviceConstants {
    /** The single band every account watches today; matches SCHEMA.md and the mock sender. */
    const val DEFAULT_DEVICE_ID = "guardband-001"
}
