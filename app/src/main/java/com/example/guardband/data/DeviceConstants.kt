package com.example.guardband.data

/**
 * Device identity until user↔band pairing exists.
 *
 * Only [RepositoryProvider] should read this; ViewModels never see a device id.
 */
object DeviceConstants {
    /** The single band every account watches today; matches SCHEMA.md and the mock sender. */
    const val DEFAULT_DEVICE_ID = "guardband-001"
}
