import org.gradle.kotlin.dsl.libs

plugins {
    kotlin("jvm")
}

dependencies {
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)

    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.postgres.jdbc)
    implementation(libs.h2.jdbc)
    

    implementation(libs.ktor.client.core)

    implementation(libs.kotlinx.coroutines.test)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.server.contentNegotiation)
    implementation(libs.ktor.client.serializationJson)
}


kotlin {
    jvmToolchain(21)
}