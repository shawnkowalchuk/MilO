plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.androidx.room3)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// --- Signing ----------------------------------------------------------------------------------
// Every build that goes on the phone is signed with one dedicated key, kept outside this
// repository. Android refuses to update an app whose signature has changed, and refuses to
// restore its backup too, so losing or changing the key means wiping every trip.
// (docs/ENGINEERING_STANDARDS.md section 12.)
//
// A build without the key is allowed only where one is expected: on GitHub's CI, or when asked
// for by name. Anywhere else a missing keystore stops the build. A quiet fallback to the debug
// key would produce an app the phone refuses to update, and Android Studio would then offer to
// uninstall the installed one, which deletes every trip.
val miloKeystore: File =
    file(
        providers
            .gradleProperty("milo.keystore.file")
            .getOrElse("${providers.systemProperty("user.home").get()}/keys/milo.jks"),
    )

// The name of the macOS Keychain entry that holds the keystore's password.
val miloKeychainEntry = "milo-keystore"

// GitHub Actions sets CI=true. The property is the by-name opt-in for any other machine.
val debugKeyAllowed: Boolean =
    providers.environmentVariable("CI").isPresent ||
        providers.gradleProperty("milo.signing.debugKey").orNull == "true"

// True while Android Studio is only reading the project's shape (a sync), not building it.
val isIdeSync: Boolean = providers.systemProperty("idea.sync.active").orNull == "true"

// What a sync is given in place of the password. Android Studio keeps everything a sync returns
// in a cache file on disk, so the real password must never be handed to one.
val syncPlaceholderPassword = "not-read-during-an-ide-sync"

// Reads the keystore password from the macOS Keychain. It is asked for only when the keystore
// exists, so this never runs on CI, where there is no `security` command.
fun readKeystorePassword(): String {
    val password =
        providers
            .exec {
                commandLine(
                    "security",
                    "find-generic-password",
                    "-a",
                    providers.systemProperty("user.name").get(),
                    "-s",
                    miloKeychainEntry,
                    "-w",
                )
                // A missing entry must reach the clear message below, not a raw exit code.
                isIgnoreExitValue = true
            }.standardOutput
            .asText
            .get()
            .trim()
    if (password.isEmpty()) {
        throw GradleException(
            "MilO's signing keystore exists at $miloKeystore, but the macOS Keychain has no " +
                "password for it under the name \"$miloKeychainEntry\". Add it with:\n" +
                "  security add-generic-password -U -a \"\$USER\" -s $miloKeychainEntry -w\n" +
                "A missing password stops the build on purpose: a build signed with any other " +
                "key could not update the app on the phone.",
        )
    }
    return password
}

room3 {
    // Room writes the tables of each database version to app/schemas as JSON. The folder is
    // committed: the phone holds real trips from phase 1 on, so every later change to a table
    // needs a migration, and a migration is written against (and checked with) these files.
    schemaDirectory("$projectDir/schemas")
}

android {
    namespace = "com.shawnkowalchuk.milo"

    // 37 is not a preference: the current stable AndroidX libraries refuse to build against less.
    compileSdk = 37

    defaultConfig {
        applicationId = "com.shawnkowalchuk.milo"
        // The app runs on one phone (Android 14, API 34). 31 is the floor because the companion
        // device and foreground-service rules the trip detection relies on changed at Android 12.
        minSdk = 31
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        when {
            miloKeystore.exists() ->
                create("milo") {
                    val password =
                        if (isIdeSync) syncPlaceholderPassword else readKeystorePassword()
                    storeFile = miloKeystore
                    storePassword = password
                    keyAlias = "milo"
                    // A PKCS12 keystore has one password for the store and the key inside it.
                    keyPassword = password
                }

            debugKeyAllowed ->
                logger.lifecycle(
                    "MilO: no signing keystore at $miloKeystore. The debug build is signed with " +
                        "the throwaway debug key and the release build is left unsigned. Never " +
                        "install either over a build signed with the real key.",
                )

            else ->
                throw GradleException(
                    "MilO's signing keystore was not found at $miloKeystore.\n" +
                        "Restore it from your backup to that path, or point the Gradle property " +
                        "milo.keystore.file at it.\n" +
                        "The build stops here on purpose: an app signed with any other key " +
                        "cannot update the one on the phone, and replacing that one deletes " +
                        "every trip.\n" +
                        "To build with the throwaway debug key anyway (never for the phone), " +
                        "add -Pmilo.signing.debugKey=true.",
                )
        }
    }

    buildTypes {
        // Android Studio's Run button installs the debug build, so that is the build that must
        // carry the real key. Release is signed the same way for when it is first needed.
        val miloSigning = signingConfigs.findByName("milo")
        if (miloSigning != null) {
            getByName("debug") { signingConfig = miloSigning }
            getByName("release") { signingConfig = miloSigning }
        }
    }

    compileOptions {
        // Java 17 is the JDK the build runs on (gradle/gradle-daemon-jvm.properties). Kotlin's
        // JVM target follows this setting under AGP 9, so it is declared once, here.
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        jniLibs {
            // The app has no native code of its own. The few .so files in the APK arrive
            // prebuilt inside AndroidX libraries. Stripping them needs the NDK version AGP 9.4
            // expects (28.2.13676358); only 27.1 is installed, and no NDK is being added for an
            // app with no native code. Without this line every build prints "Unable to strip
            // the following libraries". Packaging them untouched costs nothing that matters here.
            keepDebugSymbols += "**/*.so"
        }
    }

    lint {
        // A lint warning is a build failure, locally and in CI, so warnings cannot pile up.
        warningsAsErrors = true
        abortOnError = true

        // The three checks below fire only because something newer has been released, which would
        // turn a green build red overnight with no change to the code. Dependabot owns version
        // updates for libraries and the Android Gradle plugin; raising the target platform is a
        // deliberate change with its own behaviour review, never a side effect of a lint run.
        disable +=
            setOf(
                // "A newer version of <library> is available."
                "GradleDependency",
                // "A newer version of the Android Gradle plugin is available."
                "AndroidGradlePluginVersion",
                // "Not targeting the latest version of Android."
                "OldTargetApi",
            )
    }
}

kotlin {
    compilerOptions {
        // The Kotlin equivalent of a strict type check: a compiler warning fails the build.
        allWarningsAsErrors = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)

    // Renders @Preview functions inside Android Studio. Debug only, so it never ships.
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Storage (data/). Room generates its code with KSP; the bundled driver is the SQLite it
    // runs on.
    implementation(libs.androidx.room3.runtime)
    implementation(libs.androidx.sqlite.bundled)
    ksp(libs.androidx.room3.compiler)
    implementation(libs.androidx.datastore.preferences)

    // Room and DataStore are coroutine-only, and the app's own code uses coroutines directly
    // (the application scope, Flow), so the library is declared rather than borrowed from them.
    implementation(libs.kotlinx.coroutines.core)

    // Screens get their ViewModel through viewModel(), and learn that they have come to the
    // front again through LifecycleEventEffect.
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Navigation between screens (app/MiloNavigation.kt). Navigation 3 saves its back stack with
    // kotlinx.serialization, which is why the screen keys are @Serializable.
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.kotlinx.serialization.json)

    // Trip recording (platform/): GPS fixes from the fused location provider, and CarConnection,
    // which says whether Android Auto is connected.
    implementation(libs.play.services.location)
    implementation(libs.androidx.car.app)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
