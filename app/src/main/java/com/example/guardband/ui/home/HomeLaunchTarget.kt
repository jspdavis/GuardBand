package com.example.guardband.ui.home

/**
 * Where a notification asks the Home host to open: Alert history from an
 * alert, Settings from the watch's ongoing notification. Carried as an
 * Intent extra by [HomeActivity.launchIntent] and honoured only once the
 * gate opens ([HomeViewModel.onLaunchTargetRequested]).
 */
enum class HomeLaunchTarget { ALERT_HISTORY, SETTINGS }
