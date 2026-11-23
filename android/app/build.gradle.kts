import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// Reads local.properties (or env vars) for values that must not be hardcoded
// in source: the backend base URL and the shared app<->server API key.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun cfg(key: String, default: String): String =
    (localProps.getProperty(key) ?: System.getenv(key) ?: default)

android {
    namespace = "id.whitesecurity.antivirus"
    compileSdk = 34

    defaultConfig {
        applicationId = "id.whitesecurity.antivirus"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "1.2.0"

        buildConfigField(
            "String", "REMOTE_BASE_URL",
            "\"${cfg("REMOTE_BASE_URL", "https://api.whitesecurity.id/")}\""
        )
        buildConfigField(
            "String", "REMOTE_API_KEY",
            "\"${cfg("REMOTE_API_KEY", "")}\""
        )
        // Where the self-updater reads /antivirus/download/latest.json + the APK.
        buildConfigField(
            "String", "UPDATE_BASE_URL",
            "\"${cfg("UPDATE_BASE_URL", "https://whitesecurity.id/")}\""
        )
        // Optional, comma-separated "sha256/<base64>" OkHttp CertificatePinner
        // pins for REMOTE_BASE_URL's host. Empty by default — an unset or
        // wrong pin would hard-break connectivity, so this is opt-in once an
        // operator has computed the real pin (see server/README.md).
        buildConfigField(
            "String", "REMOTE_CERT_PINS",
            "\"${cfg("REMOTE_CERT_PINS", "")}\""
        )

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val keystorePath = cfg("RELEASE_KEYSTORE", "")
            if (keystorePath.isNotBlank() && rootProject.file(keystorePath).exists()) {
                storeFile = rootProject.file(keystorePath)
                storePassword = cfg("RELEASE_KEYSTORE_PASSWORD", "")
                keyAlias = cfg("RELEASE_KEY_ALIAS", "")
                keyPassword = cfg("RELEASE_KEY_PASSWORD", "")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-opt-in=androidx.compose.material3.ExperimentalMaterial3Api")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.3")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.3")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
