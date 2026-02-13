plugins {
    kotlin("jvm") version "2.3.10"
    `java-library`
    `maven-publish`
}

group = "com.phorest"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}

kotlin {
    jvmToolchain(11)
}

repositories {
    mavenCentral()
}

val ktorVersion = "3.4.0"
val otelProtoVersion = "1.5.0-alpha"
val junitVersion = "5.11.4"
val awaitilityVersion = "4.2.2"
val mockkVersion = "1.13.16"
val slf4jVersion = "2.0.16"
val logbackVersion = "1.4.14"

dependencies {
    // Core
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    api("io.opentelemetry.proto:opentelemetry-proto:$otelProtoVersion")
    implementation("org.slf4j:slf4j-api:$slf4jVersion")

    // JUnit 5 - compileOnly so users provide their own
    compileOnly("org.junit.jupiter:junit-jupiter-api:$junitVersion")

    // Await support (core to this test library)
    implementation("org.awaitility:awaitility-kotlin:$awaitilityVersion")

    // Test
    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation("io.ktor:ktor-client-core:$ktorVersion")
    testImplementation("io.ktor:ktor-client-cio:$ktorVersion")
    testImplementation("ch.qos.logback:logback-classic:$logbackVersion")
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "otel-test-collector"
        }
    }
}