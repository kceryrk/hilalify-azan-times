plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.kceryrk.prayertimes"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.kceryrk.prayertimes"
        minSdk = 30
        targetSdk = 36
        versionCode = 24
        versionName = "1.18"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.wear.compose:compose-material:1.7.1")
    implementation("androidx.wear.compose:compose-foundation:1.7.1")
    implementation("com.google.android.gms:play-services-wearable:20.0.1")
}
