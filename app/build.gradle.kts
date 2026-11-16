import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val releaseKeystoreProps = Properties().apply {
    val f = File(System.getProperty("user.home"), ".android/hilalify-keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.kceryrk.prayertimes"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.kceryrk.prayertimes"
        minSdk = 26
        targetSdk = 36
        versionCode = 28
        versionName = "1.22"
    }

    signingConfigs {
        if (releaseKeystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = file(releaseKeystoreProps.getProperty("storeFile"))
                storePassword = releaseKeystoreProps.getProperty("storePassword")
                keyAlias = releaseKeystoreProps.getProperty("keyAlias")
                keyPassword = releaseKeystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (releaseKeystoreProps.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    // Keep all languages in the AAB so the in-app language choice works even when the system language differs.
    bundle {
        language {
            enableSplit = false
        }
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
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("com.google.android.play:app-update-ktx:2.1.0")
    implementation("androidx.fragment:fragment:1.8.5")
    implementation("com.google.android.gms:play-services-wearable:20.0.1")
}
