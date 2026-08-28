import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

/**
 * Signing secrets live in .env (gitignored). When absent — a fresh clone, or CI without
 * secrets — release falls back to debug signing so the project always builds.
 */
val envFile = rootProject.file(".env")
val env = Properties().apply {
    if (envFile.exists()) envFile.inputStream().use { load(it) }
}
fun env(key: String): String? = (System.getenv(key) ?: env.getProperty(key))?.takeIf { it.isNotBlank() }

/** Real android.jar, located from the SDK env — never a hardcoded absolute path. */
val androidJar: File = File(
    System.getenv("ANDROID_HOME")
        ?: System.getenv("ANDROID_SDK_ROOT")
        ?: "${System.getProperty("user.home")}/Android/Sdk",
    "platforms/android-37.0/android.jar",
)

val releaseKeystore = env("KEYSTORE_PATH")?.let(::File)
val hasReleaseSigning = releaseKeystore?.exists() == true

android {
    namespace = "de.steppicrew.healthconnectview"
    compileSdk = 37

    defaultConfig {
        applicationId = "de.steppicrew.healthconnectview"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        androidResources.localeFilters += listOf(
            "en", "de", "es", "fr", "it", "pt-rBR", "nl", "pl", "tr",
            "ru", "ja", "ko", "zh-rCN", "zh-rTW", "hi", "in", "ar",
        )
    }

    if (hasReleaseSigning) {
        signingConfigs {
            create("release") {
                storeFile = releaseKeystore
                storePassword = env("KEYSTORE_PASSWORD")
                keyAlias = env("KEY_ALIAS")
                keyPassword = env("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { compose = true }

    lint {
        warningsAsErrors = true
        abortOnError = true
        checkDependencies = true
        // Machine-translated strings ship as a starting point; missing ones fall back to English.
        disable += "MissingTranslation"
    }

    testOptions.unitTests.all { it.systemProperty("java.awt.headless", "true") }
    // Real android.jar (not the stubbed one) so tests can reflect over platform constants.
    testOptions.unitTests.isReturnDefaultValues = true

    packaging.resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)

    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.navigation.compose)
    implementation(libs.coroutines.android)

    implementation(libs.health.connect)
    implementation(libs.vico.compose.m3)
    implementation(libs.billing.ktx)
    implementation(libs.datastore.preferences)

    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    // Real android.jar so tests can reflect over platform permission constants.
    // Path is derived from the SDK location, never hardcoded.
    testImplementation(files(androidJar))
}
