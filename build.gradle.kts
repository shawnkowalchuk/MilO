buildscript {
    dependencies {
        // AGP 9 compiles Kotlin itself ("built-in Kotlin") and ships with an older Kotlin Gradle
        // plugin. Putting ours on the build classpath is the documented way to choose the compiler
        // version, so Kotlin is pinned by gradle/libs.versions.toml and not by whatever AGP bundles.
        // Do not apply org.jetbrains.kotlin.android or kapt anywhere: both fail under AGP 9.
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.androidx.room3) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.spotless)
}

// Formatting is checked from the root project so one task (spotlessCheck) covers every Kotlin
// source file and every Gradle script. Spotless cannot discover Android source sets on its own,
// which is why the targets are spelled out.
spotless {
    kotlin {
        target("app/src/**/*.kt")
        ktlint(libs.versions.ktlint.get())
    }
    kotlinGradle {
        target("*.gradle.kts", "app/*.gradle.kts")
        ktlint(libs.versions.ktlint.get())
    }
}

// gradle/gradle-daemon-jvm.properties pins the JDK that runs the build, so the terminal, Android
// Studio and CI all use Java 17 regardless of which JDK each of them bundles. It is regenerated
// with ./gradlew updateDaemonJvm. The download URLs are left out deliberately: a machine without
// JDK 17 should fail with a clear message, not fetch a JDK behind the owner's back.
tasks.named<UpdateDaemonJvm>("updateDaemonJvm") {
    languageVersion = JavaLanguageVersion.of(17)
    toolchainDownloadUrls.empty()
}
