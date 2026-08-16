plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// AGP 7.4.2 + Gradle 7.6.1 — avoids the Windows dex pipeline path bug in AGP 8.x

android {
    namespace = "eu.kanade.tachiyomi.animeextension.en.animepahe"
    compileSdk = 34

    defaultConfig {
        // Extension APKs don't need a launcher — these are sideloaded into Aniyomi
        applicationId = "eu.kanade.tachiyomi.animeextension.en.animepahe"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // The extension name shown inside Aniyomi
        manifestPlaceholders["appName"] = "Render Dragon – AnimePahe"
    }

    buildTypes {
        release {
            // Extensions must NOT be minified — Aniyomi loads them by reflection
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    // Lint is irrelevant for sideloaded extension APKs and breaks on
    // Windows due to path length limits — disable it entirely.
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    // ── Aniyomi extension API stubs ───────────────────────────────────────────
    // These are compileOnly — Aniyomi provides them at runtime via its APK.
    // We compile against the stubs so our code type-checks; the actual classes
    // live inside the Aniyomi app on the user's device.
    compileOnly("com.github.aniyomiorg:extensions-lib:14") {
        // Exclude transitive deps that conflict with Android SDK
        exclude(group = "org.jetbrains.kotlin")
    }

    // ── Runtime libraries (packaged into our APK) ─────────────────────────────
    // OkHttp — HTTP client used for all web requests
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Kotlin serialization — for parsing AnimePahe's JSON API responses
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.1")

    // Injekt — lightweight DI used by Tachiyomi/Aniyomi ecosystem.
    // Use the Maven Central release; the old JitPack commit is no longer served.
    implementation("uy.kohesive.injekt:injekt-core:1.16.1")

    // AndroidX Preference — for the in-app quality preference screen
    implementation("androidx.preference:preference-ktx:1.2.1")

    // Kotlin stdlib
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.8.22")
}
