plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsKotlinAndroid)
    // EmergencyContact travels between the sign-up steps as an Intent extra.
    id("kotlin-parcelize")
    id("com.google.gms.google-services")
}

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
