plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.voxshelf"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.voxshelf"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        debug {
            // The ratings server running on this computer, reached through "adb reverse tcp:8787 tcp:8787".
            buildConfigField("String", "RATINGS_URL", "\"https://voxshelf-ratings.beneichholz9.workers.dev\"")
            manifestPlaceholders["cleartext"] = "true"
        }
        release {
            buildConfigField("String", "RATINGS_URL", "\"\"")
            manifestPlaceholders["cleartext"] = "false"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.coroutines.android)
    implementation(libs.coroutines.guava)
    implementation(libs.coil.compose)
    implementation(libs.coil.network)
}
