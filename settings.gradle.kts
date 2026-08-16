pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Aniyomi / Tachiyomi extension stubs
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "RenderDragon"
include(":app")
