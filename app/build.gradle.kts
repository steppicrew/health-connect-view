import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.play.publisher)
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

play {
    // Uploads are opt-in: without a service account this stays inert rather than failing.
    val serviceAccount = env("PLAY_SERVICE_ACCOUNT_JSON")
    enabled.set(serviceAccount != null)
    if (serviceAccount != null) serviceAccountCredentials.set(File(serviceAccount))
    defaultToAppBundles.set(true)
    track.set("internal")
}

android {
    namespace = "de.steppicrew.healthconnectview"
    compileSdk = 37

    defaultConfig {
        applicationId = "de.steppicrew.healthconnectview"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Keep only the locales this app actually translates. Without this the APK also
        // carries roughly eighty AndroidX locales it never uses.
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

    testOptions.unitTests.all { test ->
        test.systemProperty("java.awt.headless", "true")
        // TranslationsTest reads src/main/res directly rather than through generated R
        // classes, so Gradle cannot infer the dependency and would serve a cached pass after
        // a string was added without its translation -- the exact case the test exists to
        // catch. Declaring the directory makes the resources a real input.
        test.inputs.dir("src/main/res")
            .withPropertyName("stringResources")
            .withPathSensitivity(org.gradle.api.tasks.PathSensitivity.RELATIVE)
    }
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

/**
 * Privacy gate: the debug navigation backdoor must never reach a shipped build.
 *
 * DebugNav exists twice, in src/debug and src/release, so release *should* compile the
 * inert variant that returns null. "Should" is the problem: a source-set mistake, a stray
 * import, or a future refactor that collapses the two would ship an app whose launch intent
 * can drive its UI -- and nothing would say so. Checked rather than trusted.
 *
 * The debug activities are caught by the manifest scan above; this catches the code path,
 * which has no manifest entry to scan.
 */
val verifyNoDebugBackdoor by tasks.registering {
    // Must run *after* the release classes exist, or it inspects an empty directory and
    // passes vacuously -- a gate that always succeeds is worse than none, because it is
    // believed.
    dependsOn("compileReleaseKotlin")

    val classes = layout.buildDirectory.dir("intermediates/built_in_kotlinc/release")
    doLast {
        val dir = classes.get().asFile
        // Deliberately not a silent pass: if the classes are missing, the check could not be
        // performed, and saying so is the only honest outcome.
        check(dir.exists()) { "Release classes not found; cannot verify the backdoor is absent" }

        // The debug variant reads an intent extra by name; the release variant is a bare
        // `return null` with no such constant. Finding the extra's name together with the
        // call that reads it therefore means the debug implementation has been compiled in.
        val offenders = dir.walkTopDown()
            .filter { it.name.endsWith(".class") }
            .filter { file ->
                val bytes = file.readText(Charsets.ISO_8859_1)
                "EXTRA_ROUTE" in bytes || ("getStringExtra" in bytes && "route" in bytes)
            }
            .toList()
        check(offenders.isEmpty()) {
            "Debug navigation backdoor compiled into release: $offenders"
        }

        val debugOnly = dir.walkTopDown()
            .filter { it.name.endsWith(".class") && it.path.contains("healthconnectview/debug/") }
            .toList()
        check(debugOnly.isEmpty()) {
            "Debug-only classes compiled into release: $debugOnly"
        }
    }
}

tasks.named("check") { dependsOn(verifyNoNetworkPermission, verifyNoDebugBackdoor) }

// Wired into the release build itself, not only into `check`: a gate that protects a shipped
// artefact has to run when that artefact is produced, or it protects nothing on the day
// someone runs assembleRelease or bundleRelease directly.
tasks.matching { it.name == "assembleRelease" || it.name == "bundleRelease" }.configureEach {
    dependsOn(verifyNoNetworkPermission, verifyNoDebugBackdoor)
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
