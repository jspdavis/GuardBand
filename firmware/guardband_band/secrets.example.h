// Copy this file to secrets.h (same folder) and fill it in.
// secrets.h is gitignored, so your Wi-Fi password never reaches the repo.
#pragma once

const char* WIFI_SSID = "YOUR_WIFI_NAME";      // must be 2.4 GHz
const char* WIFI_PASS = "YOUR_WIFI_PASSWORD";

// Optional: the project's web API key, which lets the band sign in to
// Firebase as itself (its band identity). It is "current_key" under
// "api_key" in app/google-services.json. It names the project rather than
// unlocking anything, but it stays here so no source file carries it.
// Leave this line out and the band writes to guardband-001 without signing
// in, as before.
#define FIREBASE_API_KEY "YOUR_FIREBASE_WEB_API_KEY"
