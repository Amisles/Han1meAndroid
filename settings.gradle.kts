pluginManagement {
    repositories {
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
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "hanime"
include(":app")
include(":core:common")
include(":core:ui")
include(":data")
include(":domain:model")
include(":feature-home")
include(":feature-search")
include(":feature-detail")
include(":feature-download")
include(":feature-profile")
include(":feature-settings")
 