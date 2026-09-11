plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.yadavar.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.yadavar.app"
        minSdk = 24
        targetSdk = 36

        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {

    // Compose BOM
    val composeBom =
        platform("androidx.compose:compose-bom:2026.08.00")

    implementation(composeBom)

    // Android
    implementation(
        "androidx.activity:activity-compose:1.13.0"
    )

    // Compose UI
    implementation(
        "androidx.compose.ui:ui"
    )

    implementation(
        "androidx.compose.ui:ui-tooling-preview"
    )

    // Material 3
    implementation(
        "androidx.compose.material3:material3"
    )

    // Material Icons
    implementation(
        "androidx.compose.material:material-icons-extended"
    )

    // Preview / Debug
    debugImplementation(
        "androidx.compose.ui:ui-tooling"
    )
}
