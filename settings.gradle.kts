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
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "StackOverFlow Home Assignment"
include(":app")
include(":core:common")
include(":core:designsystem")
include(":feature:home")
include(":feature:detail")
include(":stackoverflowsdk")
include(":stackoverflowsdk:api")
include(":stackoverflowsdk:domain")
include(":stackoverflowsdk:data")
include(":core:dispatcher")
