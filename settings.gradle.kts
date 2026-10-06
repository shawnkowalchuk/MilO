pluginManagement {
    repositories {
        // Restricting Google's repository to the groups it actually hosts stops Gradle asking it
        // for every other plugin first, and makes it obvious where each plugin comes from.
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // A module that declares its own repository fails the build. Every artifact in the app must
    // come from the two repositories below, so there is one place to audit where code comes from.
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "MilO"

// One module on purpose: features are packages inside :app (see docs/ENGINEERING_STANDARDS.md).
include(":app")
