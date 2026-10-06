plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
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

    testImplementation(libs.junit)
}
