// SPDX-License-Identifier: GPL-3.0-only

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google()
        mavenCentral()
        // Kadb's SPAKE2 dependency (wireless-debugging pairing) is only published on JitPack.
        exclusiveContent {
            forRepository { maven("https://jitpack.io") }
            filter { includeGroup("com.github.Flyfish233") }
        }
    }
}

rootProject.name = "tellyscope"
include(":app", ":adb", ":cli")
