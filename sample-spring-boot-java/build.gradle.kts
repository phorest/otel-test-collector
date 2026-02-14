plugins {
    java
    id("org.springframework.boot") version "3.4.4"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.phorest"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

repositories {
    mavenCentral()
}

val otelAgentVersion = "2.12.0"

val otelAgent: Configuration by configurations.creating

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("io.opentelemetry:opentelemetry-api:1.47.0")

    otelAgent("io.opentelemetry.javaagent:opentelemetry-javaagent:$otelAgentVersion")

    testImplementation(project(":"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

}

val copyOtelAgent by tasks.registering(Copy::class) {
    from(otelAgent)
    into(layout.buildDirectory.dir("otel"))
}

tasks.test {
    dependsOn(copyOtelAgent)
    useJUnitPlatform()

    val agentJar = otelAgent.singleFile
    jvmArgs(
        "-javaagent:${layout.buildDirectory.file("otel/${agentJar.name}").get().asFile.absolutePath}"
    )
    environment("OTEL_SERVICE_NAME", "sample-spring-boot-java")
    environment("OTEL_EXPORTER_OTLP_PROTOCOL", "http/protobuf")
    environment("OTEL_EXPORTER_OTLP_ENDPOINT", "http://localhost:4318")
    environment("OTEL_TRACES_EXPORTER", "otlp")
    environment("OTEL_METRICS_EXPORTER", "none")
    environment("OTEL_LOGS_EXPORTER", "none")

    // Export spans quickly so tests don't wait on the default 5s batch delay
    environment("OTEL_BSP_SCHEDULE_DELAY", "100")
    environment("OTEL_BSP_MAX_EXPORT_BATCH_SIZE", "1")

    // Disable context propagation so TestRestTemplate doesn't inject trace headers
    // into requests — server spans remain independent roots
    environment("OTEL_PROPAGATORS", "none")

    // Suppress expected connection errors during shutdown when collector is already stopped
    systemProperty("otel.javaagent.logging", "simple")
    systemProperty("io.opentelemetry.javaagent.slf4j.simpleLogger.log.io.opentelemetry.exporter.internal.http.HttpExporter", "off")
}
