plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    implementation(projects.server.types)
    
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.logging)

    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.server.swagger)
    implementation(libs.ktor.server.partialContent)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.autoHead)
    implementation(libs.ktor.server.serializationJson)
    implementation(libs.ktor.server.contentNegotiation)
    implementation(libs.ktor.server.requestValidation)
    implementation(libs.ktor.server.doubleReceive)
    
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.websocket)
    implementation(libs.ktor.client.contentNegotiation)
    implementation(libs.ktor.client.serializationJson)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.postgres.jdbc)
    implementation(libs.h2.jdbc)
    
    implementation(libs.gradle.kotlinx.serialization)
}

kotlin {
    jvmToolchain(17)
}