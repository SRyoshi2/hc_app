import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
}

// Signatur für Release-Builds: Pfad und Passwörter stehen NIE im Repo, sondern in keystore.properties
// (lokal, in .gitignore) oder in Umgebungsvariablen (GitHub Actions, siehe .github/workflows/release.yml).
// Ohne beides bleibt das Release-APK unsigniert; Debug-Builds sind davon nicht betroffen.
val signierung = Properties().apply {
    val datei = rootProject.file("keystore.properties")
    if (datei.exists()) datei.inputStream().use { load(it) }
}

fun signWert(eigenschaft: String, umgebung: String): String? =
    signierung.getProperty(eigenschaft)?.takeIf { it.isNotBlank() } ?: System.getenv(umgebung)?.takeIf { it.isNotBlank() }

val keystorePfad = signWert("storeFile", "HC_KEYSTORE_FILE")

android {
    namespace = "de.sryoshi.hcapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "de.sryoshi.hcapp"
        minSdk = 26
        targetSdk = 35
        // Updates setzen eine höhere versionCode voraus. Lokal bleibt es bei 1, GitHub setzt sie je Lauf hoch.
        versionCode = System.getenv("HC_VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("HC_VERSION_NAME")?.takeIf { it.isNotBlank() } ?: "0.1.0"
    }

    signingConfigs {
        if (keystorePfad != null) {
            create("release") {
                storeFile = rootProject.file(keystorePfad)
                storePassword = signWert("storePassword", "HC_KEYSTORE_PASSWORD")
                keyAlias = signWert("keyAlias", "HC_KEY_ALIAS")
                keyPassword = signWert("keyPassword", "HC_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystorePfad != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core"))

    implementation(libs.core.ktx)
    implementation(libs.coroutines.android)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.coil.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons)
    implementation(libs.compose.tooling.preview)
    debugImplementation(libs.compose.tooling)
}
