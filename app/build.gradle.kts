import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsKotlinAndroid)
    // EmergencyContact travels between the sign-up steps as an Intent extra.
    id("kotlin-parcelize")
    id("com.google.gms.google-services")
}

// The password-reset App Link, in ONE place.
//
// This is Firebase's OWN default action handler, not a page of ours. The
// project cannot change the Console's action URL (Firebase refuses it with
// EMAIL_TEMPLATE_UPDATE_NOT_ALLOWED), so instead of moving the link the app
// claims the one Firebase already sends:
//   https://guardband-aae65.firebaseapp.com/__/auth/action?mode=resetPassword&oobCode=...
// firebaseapp.com serves the same Hosting site as web.app, so the
// assetlinks.json deployed from hosting/ verifies this host too. Without the
// app, the link opens Firebase's own reset page, which completes the reset in
// the browser.
//
// Three files have to agree on these: the manifest's App Link intent filter,
// the parser that checks the incoming URL, and
// hosting/public/.well-known/assetlinks.json. They are pushed out as manifest
// placeholders and BuildConfig fields rather than written three times. See
// docs/forgot-password.md.
//
// Never host a page of our own under /__/ : Firebase serves that prefix itself,
// so a deployed file there is never reached. Claiming Firebase's handler is the
// one exception, and only at this exact path. The manifest uses android:path,
// never pathPrefix, because /__/auth/handler (OAuth redirects) is on the same
// host.
val resetLinkHost = "guardband-aae65.firebaseapp.com"
val resetLinkPath = "/__/auth/action"

// The Hosting domain for the static pages the app opens. Linked from the
// consent screen and from Settings, and both are reachable without the app,
// which is the point: someone who has deleted the app still needs to be able
// to read the notice and find out how to get their data removed.
val hostingHost = "guardband-aae65.web.app"
val privacyPath = "/privacy"
val deleteAccountPath = "/delete-account"

// The Track map's MapTiler Cloud key, from local.properties (gitignored), so
// it is never in a tracked file. A machine without the line still builds,
// with an empty key, and the map then says it is unavailable instead of
// falling back to OpenStreetMap's public servers. The key ships inside the
// APK, so its protection is the user-agent restriction on MapTiler's side
// plus the plan's monthly cap. See docs/osmdroid.md.
val mapTilerApiKey: String = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}.getProperty("MAPTILER_API_KEY", "").trim()

android {
    namespace = "com.example.guardband"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.guardband"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Read by AndroidManifest.xml's App Link intent filter.
        manifestPlaceholders["resetLinkHost"] = resetLinkHost
        manifestPlaceholders["resetLinkPath"] = resetLinkPath

        // RESET_LINK_HOST + RESET_LINK_PATH are read by ResetPasswordActivity,
        // which hands them to ResetLinkParser. The other three are read by
        // HostedPages, which builds the URLs the consent screen and Settings
        // open.
        buildConfigField("String", "RESET_LINK_HOST", "\"$resetLinkHost\"")
        buildConfigField("String", "RESET_LINK_PATH", "\"$resetLinkPath\"")
        buildConfigField("String", "HOSTING_HOST", "\"$hostingHost\"")
        buildConfigField("String", "PRIVACY_PATH", "\"$privacyPath\"")
        buildConfigField("String", "DELETE_ACCOUNT_PATH", "\"$deleteAccountPath\"")

        // Read only by ui/track/MapTilerTileSource.
        buildConfigField("String", "MAPTILER_API_KEY", "\"$mapTilerApiKey\"")
    }

    // buildConfigField needs this explicitly from AGP 8 onwards.
    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    testOptions {
        // Robolectric needs the merged resources and the real android.jar
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    // MVVM — ViewModel, lifecycle-aware coroutines, by viewModels()
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)

    // Home host — tab Fragments (by viewModels/activityViewModels) and lists
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.recyclerview)

    // Track tab map — MapTiler raster tiles (ui/track/MapTilerTileSource).
    // Configured in one place by ui/track/OsmdroidConfig (user agent +
    // app-specific tile cache).
    implementation(libs.osmdroid.android)

    // Track avatar - the Google profile photo, circle-cropped. Coil's own
    // defaults (memory + disk cache in cacheDir, no logger); see
    // ui/track/AvatarPhoto for which URLs it is ever given.
    implementation(libs.coil)

    // OkHttp — the debug-only MockSender harness (src/debug) calls it directly.
    // Release still ships OkHttp, transitively, as Coil's network fetcher.
    debugImplementation(libs.okhttp)

    // CardView — used in activity_dashboard.xml status card
    // TODO: remove once the dead ui/dashboard files and activity_dashboard.xml are deleted
    implementation("androidx.cardview:cardview:1.0.0")

    // Firebase — the BoM pins every version below it, so none declares its own
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.database)

    // Google sign-in - Credential Manager with Google ID tokens. The legacy
    // GoogleSignIn API is deliberately not used. credentials-play-services-auth
    // pulls in play-services-auth transitively as its backend provider; no code
    // imports it.
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    // await() on Firebase's Task<T>, so the repositories stay suspend functions
    implementation(libs.kotlinx.coroutines.play.services)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // Robolectric — InputValidator uses android.util.Patterns, which is stubbed
    // out in plain JVM unit tests
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
