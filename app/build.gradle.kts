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

/**
 * Privacy gate: the app must never gain network access, not even transitively. A dependency
 * that declares INTERNET or ACCESS_NETWORK_STATE would otherwise be merged in silently.
 */
val verifyNoNetworkPermission by tasks.registering {
    val manifests = layout.buildDirectory.dir("intermediates/merged_manifest")
    doLast {
        val offenders = manifests.get().asFile.walkTopDown()
            .filter { it.name == "AndroidManifest.xml" && !it.path.contains("Test") }
            .filter { file ->
                val text = file.readText()
                "android.permission.INTERNET" in text ||
                    "android.permission.ACCESS_NETWORK_STATE" in text
            }
            .toList()
        check(offenders.isEmpty()) {
            "Network permission leaked into the merged manifest: $offenders"
        }

        // The debug seeder needs WRITE access; release must never ship it.
        val releaseWrites = manifests.get().asFile.walkTopDown()
            .filter { it.name == "AndroidManifest.xml" && it.path.contains("release") }
            .filter { "permission.health.WRITE" in it.readText() }
            .toList()
        check(releaseWrites.isEmpty()) {
            "Release build requests write access to health data: $releaseWrites"
        }
    }
}

tasks.named("check") { dependsOn(verifyNoNetworkPermission) }

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
    implementation(libs.billing.ktx) {
        // Play Billing drags in Google's datatransport/Firebase telemetry uploader. The app
        // has no INTERNET permission so it could never transmit, but a privacy-focused health
        // app should not ship a dormant analytics pipeline at all.
        exclude(group = "com.google.android.datatransport")
        exclude(group = "com.google.firebase")
    }
    implementation(libs.datastore.preferences)

    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    // Real android.jar so tests can reflect over platform permission constants.
    // Path is derived from the SDK location, never hardcoded.
    testImplementation(files(androidJar))
}
