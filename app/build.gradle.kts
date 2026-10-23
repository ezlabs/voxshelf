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
            // The ratings server running on this computer, as the emulator sees it.
            buildConfigField("String", "RATINGS_URL", "\"http://10.0.2.2:8787\"")
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
}
