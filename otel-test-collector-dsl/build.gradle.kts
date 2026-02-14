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

val junitVersion = "5.11.4"

dependencies {
    api(project(":"))

    compileOnly("org.junit.jupiter:junit-jupiter-api:$junitVersion")

    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    testImplementation(testFixtures(project(":")))
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "otel-test-collector-dsl"
        }
    }
}
