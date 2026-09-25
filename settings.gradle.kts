pluginManagement {
    repositories {
        // Mirror Maven Central milik Google; Maven Central langsung sering membalas 429 (D-04).
        maven("https://maven-central.storage-download.googleapis.com/maven2")
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // Mirror Maven Central milik Google; Maven Central langsung sering membalas 429 (D-04).
        maven("https://maven-central.storage-download.googleapis.com/maven2")
        google()
        mavenCentral()
    }
}

rootProject.name = "catat-uang"

include(":core:engine")

// Modul Android hanya disertakan jika Android SDK tersedia, supaya :core:engine
// tetap bisa di-build & dites di mesin tanpa SDK (lihat CLAUDE.md bagian 16, D-02).
val localProps = file("local.properties").takeIf { it.exists() }?.readLines().orEmpty()
val hasAndroidSdk = System.getenv("ANDROID_HOME") != null ||
    System.getenv("ANDROID_SDK_ROOT") != null ||
    localProps.any { it.trim().startsWith("sdk.dir") }
if (hasAndroidSdk) {
    include(":app")
} else {
    logger.warn("Android SDK tidak ditemukan: modul :app dilewati, hanya :core:engine yang di-build.")
}
