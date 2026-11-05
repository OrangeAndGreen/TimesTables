plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.viggi.timestables"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.viggi.timestables"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        // CI appends its run number so Obtainium can match the installed build to
        // the release tag. versionCode stays put so local installs never downgrade.
        versionName = System.getenv("CI_BUILD_NUMBER")?.let { "1.0.$it" } ?: "1.0"
    }

    // CI signs release builds with Dave's PC debug keystore (from repo secrets),
    // so its APKs install over Android Studio builds and vice versa.
    val ciKeystore = System.getenv("SIGNING_KEYSTORE")?.let { file(it) }
    signingConfigs {
        if (ciKeystore != null) {
            create("ci") {
                storeFile = ciKeystore
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            if (ciKeystore != null) signingConfig = signingConfigs.getByName("ci")
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    testImplementation(libs.junit)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
