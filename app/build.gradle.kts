plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.cliqkx.renderdragon.animepahe"
    compileSdk = 34

    defaultConfig {
        // Unique package ID so Android does not treat this as the
        // existing official AnimePahe extension.
        applicationId = "com.cliqkx.renderdragon.animepahe"

        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        manifestPlaceholders["appName"] = "Render Dragon – AnimePahe"
    }

    buildTypes {
        release {
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

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

/*
 * Prevent JVM-only coroutine debugging classes from entering
 * the Android dex pipeline.
 */
configurations.configureEach {
    exclude(
        group = "org.jetbrains.kotlinx",
        module = "kotlinx-coroutines-debug",
    )
}

dependencies {
    // Aniyomi extension API
    compileOnly("com.github.aniyomiorg:extensions-lib:14") {
        exclude(group = "org.jetbrains.kotlin")
    }

    // HTTP
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.11.0")

    // JSON
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.1")

    // Injekt
    implementation("uy.kohesive.injekt:injekt-core:1.16.1")

    // Preferences
    implementation("androidx.preference:preference-ktx:1.2.1")

    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.8.22")
}