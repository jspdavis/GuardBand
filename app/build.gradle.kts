plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsKotlinAndroid)
    // EmergencyContact travels between the sign-up steps as an Intent extra.
    id("kotlin-parcelize")
    id("com.google.gms.google-services")
}

// The Hosting domain and the paths served from it, in ONE place.
//
// Firebase appends ?mode=resetPassword&oobCode=... to whatever action URL the
// Console's "Password reset" email template points at. These two values are
// what makes that URL open GuardBand instead of Firebase's hosted page, so
// they have to agree across three files at once: the manifest's App Link
// intent filter, the parser that checks the incoming URL, and
// hosting/public/.well-known/assetlinks.json.
//
// They are declared here and pushed out as a manifest placeholder and a
// BuildConfig field rather than written three times. Change them here and
// redeploy hosting; see docs/forgot-password.md.
//
// Do NOT use a path under /__/auth/ : that prefix is reserved by Firebase
// Hosting for its own reserved URLs.
val hostingHost = "guardband-aae65.web.app"

// The App Link target. Its host doubles as the host for every hosted page
// below, which is why the constant is not called resetLinkHost any more.
val resetLinkPath = "/reset"

// Static pages served from the same domain. Linked from the consent screen and
// from Settings, and both are reachable without the app, which is the point:
// someone who has deleted the app still needs to be able to read the notice
// and find out how to get their data removed.
val privacyPath = "/privacy"
val deleteAccountPath = "/delete-account"

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
        manifestPlaceholders["hostingHost"] = hostingHost
        manifestPlaceholders["resetLinkPath"] = resetLinkPath

        // HOSTING_HOST + RESET_LINK_PATH are read by ResetPasswordActivity,
        // which hands them to ResetLinkParser. The other two are read by
        // HostedPages, which builds the URLs the consent screen and Settings
        // open. One host for all three, so a domain change is one edit here.
        buildConfigField("String", "HOSTING_HOST", "\"$hostingHost\"")
        buildConfigField("String", "RESET_LINK_PATH", "\"$resetLinkPath\"")
        buildConfigField("String", "PRIVACY_PATH", "\"$privacyPath\"")
        buildConfigField("String", "DELETE_ACCOUNT_PATH", "\"$deleteAccountPath\"")
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

    // Track tab map — OpenStreetMap tiles, no API key. Configured in one place
    // by ui/track/OsmdroidConfig (user agent + app-specific tile cache).
    implementation(libs.osmdroid.android)

    // OkHttp — only the debug-only MockSender harness (src/debug) uses it
    debugImplementation(libs.okhttp)

    // CardView — used in activity_dashboard.xml status card
    // TODO: remove once the dead ui/dashboard files and activity_dashboard.xml are deleted
    implementation("androidx.cardview:cardview:1.0.0")

    // Firebase — the BoM pins every version below it, so none declares its own
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
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
