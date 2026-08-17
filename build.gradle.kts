plugins {
    id("com.android.application") version "8.7.3"
}

android {
    namespace = "com.walkieoffline"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.walkieoffline"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    sourceSets {
        getByName("main") {
            java.srcDirs(".")
            manifest.srcFile("AndroidManifest.xml")
        }
    }
}
