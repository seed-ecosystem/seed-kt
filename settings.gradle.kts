rootProject.name = "seed-backend"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
include("application")
include("server")
include("server:api")
findProject(":server:api")?.name = "api"
include("server:usecase")
findProject(":server:usecase")?.name = "usecase"
include("server:database")
findProject(":server:database")?.name = "database"
include("server:types")
findProject(":server:types")?.name = "types"
