plugins {
    id("com.android.application")
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
dependencies {
    implementation("com.google.android.gms:play-services-nearby:19.3.0")
}
