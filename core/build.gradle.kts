plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.jas0n1ee.wixtar.chronos.core"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.datastore.preferences)
    implementation(libs.okhttp)
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.guava)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
}
