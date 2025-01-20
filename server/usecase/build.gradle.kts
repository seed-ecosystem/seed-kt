plugins {
    kotlin("jvm")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.gradle.kotlinx.serialization)
    implementation(projects.server.types)
    implementation(projects.server.api)
    implementation(projects.server.database)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.postgres.jdbc)
    implementation(libs.h2.jdbc)
}
kotlin {
    jvmToolchain(17)
}