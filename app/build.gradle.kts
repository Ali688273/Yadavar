plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.yadavar.app"

    compileSdk = 35

    defaultConfig {
        applicationId = "com.yadavar.app"

        minSdk = 24

        // Android 14
        targetSdk = 34

        versionCode = 2
        versionName = "1.1.0"
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

    // Activity
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

    // Debug tools
    debugImplementation(
        "androidx.compose.ui:ui-tooling"
    )
}
