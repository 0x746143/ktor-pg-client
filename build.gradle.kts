plugins {
    kotlin("jvm") version "2.0.21"
}

group = "com.github.x746143"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.ktor.network)
    implementation(libs.scram.client)
    testImplementation(kotlin("test"))
    testImplementation(libs.mockk)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.logback.classic)
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}