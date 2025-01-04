plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    implementation(libs.gradle.kotlinx.serialization)
    implementation(libs.kotlinx.serialization.json)
}

kotlin {
    jvmToolchain(21)
}