pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        // JetBrains cache redirector for Google Maven (helps in restricted networks)
        maven("https://cache-redirector.jetbrains.com/maven.google.com")
        maven("https://cache-redirector.jetbrains.com/dl.google.com/dl/android/maven2")
        // Mirrors to improve access in China or restricted networks
        maven("https://maven.aliyun.com/repository/google") {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        mavenCentral()
        gradlePluginPortal()
    }
    resolutionStrategy {
        eachPlugin {
            when (requested.id.id) {
                "com.android.application" -> useModule("com.android.tools.build:gradle:${requested.version}")
                "com.android.library" -> useModule("com.android.tools.build:gradle:${requested.version}")
            }
        }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        // JetBrains cache redirector for Google Maven (helps in restricted networks)
        maven("https://cache-redirector.jetbrains.com/maven.google.com")
        maven("https://cache-redirector.jetbrains.com/dl.google.com/dl/android/maven2")
        // Mirrors to improve access in China or restricted networks
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/public")
        mavenCentral()
    }
}

rootProject.name = "watch view"
include(":app")
