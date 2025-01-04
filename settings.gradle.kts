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

class GitHubUnauthorizedException(env: String)
    : Exception("We could not find your credentials for GitHub. Check if the $env environment variable set")

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        google()
        maven {
            url = uri("https://maven.pkg.github.com/meetacy/maven")
            credentials {
                username = System.getenv("GITHUB_USERNAME")
                    ?: System.getenv("USERNAME")
                            ?: throw GitHubUnauthorizedException("GITHUB_USERNAME")
                password = System.getenv("GITHUB_TOKEN") ?: throw GitHubUnauthorizedException("GITHUB_TOKEN")
            }
        }
        
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
