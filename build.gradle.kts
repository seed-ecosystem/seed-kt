buildscript {
    repositories {
        mavenCentral()
        mavenLocal()
        google()
        gradlePluginPortal()
    }

    dependencies {
        classpath(libs.gradle.kotlin)
        classpath(libs.gradle.kotlinx.serialization)
    }
}