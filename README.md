# OTel Test Collector

A lightweight, in-process OpenTelemetry collector for testing. It starts an OTLP HTTP server that captures spans emitted by your application, then provides a fluent API to query and assert against them.

No external infrastructure required — just add the dependency and write tests.

## Modules

| Module | Artifact | Description |
|--------|----------|-------------|
| `otel-test-collector` | `com.phorest:otel-test-collector` | Core collector, JUnit 5 extension, and assertion API |
| `otel-test-collector-dsl` | `com.phorest:otel-test-collector-dsl` | Kotlin DSL for querying and asserting trace structures |

## Installation

This library is published via [JitPack](https://jitpack.io). Add the JitPack repository and the dependency to your build file.

### Gradle (Kotlin DSL)

```kotlin
repositories {
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    // Kotlin projects — DSL includes the core module transitively
    testImplementation("com.github.phorest.otel-test-collector:otel-test-collector-dsl:main-SNAPSHOT")

    // Java projects — core module only
    testImplementation("com.github.phorest.otel-test-collector:otel-test-collector:main-SNAPSHOT")
}
```

### Gradle (Groovy DSL)

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    // Kotlin projects
    testImplementation 'com.github.phorest.otel-test-collector:otel-test-collector-dsl:main-SNAPSHOT'

    // Java projects
    testImplementation 'com.github.phorest.otel-test-collector:otel-test-collector:main-SNAPSHOT'
}
```

### Maven

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.phorest.otel-test-collector</groupId>
    <!-- Use otel-test-collector-dsl for Kotlin, otel-test-collector for Java -->
    <artifactId>otel-test-collector</artifactId>
    <version>main-SNAPSHOT</version>
    <scope>test</scope>
</dependency>
```

> **Tip:** Replace `main-SNAPSHOT` with a specific commit hash or tag for reproducible builds.

## Quick Start

### Kotlin (with DSL)

```kotlin
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class HelloControllerOtelTest {

    companion object {
        @JvmField
        @RegisterExtension
        val collector = OtlpCollectorExtension.builder()
            .port(4318)
            .resetBeforeEach(true)
            .build()
    }

    @Autowired
    lateinit var restTemplate: TestRestTemplate

    @Test
    fun `captures HTTP server span`() {
        restTemplate.getForEntity<String>("/hello")

        collector.awaitSpanMatching {
            withNameContaining("hello")
        }.assertThat {
            hasKind(Span.SpanKind.SPAN_KIND_SERVER)
        }
    }
}
```

### Java

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HelloControllerOtelTest {

    @RegisterExtension
    static OtlpCollectorExtension collector = OtlpCollectorExtension.builder()
            .port(4318)
            .resetBeforeEach(true)
            .build();

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void capturesHttpServerSpan() {
        restTemplate.getForEntity("/hello", String.class);

        collector.awaitSpan(span -> 
                        span.getName().contains("hello")
        ).assertThat()
                .hasKind(Span.SpanKind.SPAN_KIND_SERVER);
        }
}
```

## OpenTelemetry Agent Configuration

Configure the OTel Java agent to export traces to the test collector. Each setting serves a specific purpose:

```kotlin
// build.gradle.kts
tasks.test {
    jvmArgs("-javaagent:path/to/opentelemetry-javaagent.jar")

    // Point the agent at the test collector
    environment("OTEL_EXPORTER_OTLP_PROTOCOL", "http/protobuf")
    environment("OTEL_EXPORTER_OTLP_ENDPOINT", "http://localhost:4318")

    // Only export traces — metrics and logs add noise and are not captured by this collector
    environment("OTEL_TRACES_EXPORTER", "otlp")
    environment("OTEL_METRICS_EXPORTER", "none")
    environment("OTEL_LOGS_EXPORTER", "none")

    // Flush spans quickly so tests don't wait on the default 5-second batch delay.
    // Without this, awaitSpans() calls would take at least 5s to resolve.
    environment("OTEL_BSP_SCHEDULE_DELAY", "100")
    environment("OTEL_BSP_MAX_EXPORT_BATCH_SIZE", "1")

    // Disable context propagation so test HTTP clients (e.g. TestRestTemplate) don't
    // inject trace headers into requests — keeps server spans as independent roots,
    // making assertions simpler.
    environment("OTEL_PROPAGATORS", "none")

    // Suppress noisy connection-refused errors from the agent when the collector
    // shuts down before the agent's final flush. These are expected and harmless.
    systemProperty("otel.javaagent.logging", "simple")
    systemProperty(
        "io.opentelemetry.javaagent.slf4j.simpleLogger.log.io.opentelemetry.exporter.internal.http.HttpExporter",
        "off"
    )
}
```

## JUnit 5 Extension

The `OtlpCollectorExtension` manages the collector lifecycle automatically.

### Programmatic Registration

```kotlin
companion object {
    @JvmField
    @RegisterExtension
    val collector = OtlpCollectorExtension.builder()
        .port(4318)              // OTLP HTTP port (default: 4318, use 0 for random)
        .resetBeforeEach(true)   // Clear spans before each test (default: true)
        .awaitTimeout(Duration.ofSeconds(15))  // Timeout for await methods
        .shared()                // Share collector across test classes
        .build()
}
```

### Annotation-Based Configuration

```kotlin
@OtlpCollectorConfig(
    port = 4318,
    resetBeforeEach = true,
    awaitTimeoutMs = 10_000
)
class MyTest { ... }
```

### Parameter Injection

The extension supports JUnit 5 parameter injection:

```kotlin
@Test
fun `test with injected collector`(collector: OtlpTestCollector) {
    // use collector directly
}
```

## Standalone Collector (Without JUnit)

```kotlin
val collector = OtlpTestCollector.builder()
    .port(4318)
    .build()
    .start()

// ... run your application, then:

val spans = collector.awaitSpans(3)
collector.close()
```

## Waiting for Spans

All await methods block until the condition is met or the timeout expires.

### Kotlin

```kotlin
// Wait for a specific number of spans
val spans = collector.awaitSpans(3)

// Wait for a span matching a predicate
val span = collector.awaitSpan { it.name == "GET /api" }

// Wait for a trace matching criteria (DSL)
val trace = collector.awaitTraceMatching {
    containsSpanNamed("GreetingService.greet")
}

// Wait for a span matching criteria (DSL)
val span = collector.awaitSpanMatching {
    withKind(Span.SpanKind.SPAN_KIND_SERVER)
    withNameContaining("/api")
}
```

### Java

```java
// Wait for a specific number of spans
var spans = collector.awaitSpans(3);

// Wait for a span matching a predicate
var span = collector.awaitSpan(s -> s.getName().equals("GET /api"));

// Wait for a trace matching criteria
var trace = collector.awaitTrace(t ->
        t.spanNames().contains("GreetingService.greet"));
```

## Querying Spans (Kotlin DSL)

The DSL module provides a type-safe query builder:

```kotlin
// Find spans matching criteria
val result = collector.spans {
    withName("GET /api")
    withKind(Span.SpanKind.SPAN_KIND_SERVER)
    withAttribute("http.method", "GET")
}
result.first()   // first matching span
result.single()  // exactly one match (throws if not)
result.all()     // all matching spans
result.count()   // number of matches
result.none()    // true if no matches

// Find a specific trace
val trace = collector.findTrace {
    containsSpanNamed("DB query")
    hasMinSpanCount(3)
}

// Find traces containing a span matching multiple criteria
val traces = collector.findTraces {
    containsSpan {
        withNameContaining("POST")
        withKind(Span.SpanKind.SPAN_KIND_SERVER)
    }
}
```

## Assertions

### Span Assertions

#### Kotlin (DSL)

```kotlin
collector.awaitSpanMatching {
    withName("GreetingService.greet")
}.assertThat {
    hasKind(Span.SpanKind.SPAN_KIND_INTERNAL)
    hasAttribute("greeting.name", "Darek")
    hasStatusOk()
    hasEvent("greeting.generated") {
        hasAttribute("message", "Hello, Darek!")
    }
}
```

#### Kotlin (Fluent API)

```kotlin
SpanAssert.assertThat(span)
    .hasName("GET /api")
    .hasKind(Span.SpanKind.SPAN_KIND_SERVER)
    .hasAttribute("http.method", "GET")
    .hasAttributeMatching("http.url", Regex(".*api.*"))
    .hasStatusOk()
    .hasEvent("exception")
    .hasDurationLessThan(Duration.ofSeconds(5))
    .hasNoParent()
```

#### Java

```java
collector.awaitSpan(s -> s.getName().equals("GreetingService.greet"))
        .assertThat()
        .hasAttribute("greeting.name", "Darek")
        .hasStatusOk()
        .hasEvent("exception", event -> {
            event.hasAttribute("exception.type", "java.lang.IllegalStateException");
            event.hasAttribute("exception.message", "Something went wrong");
        });
```

### Trace Tree Assertions

The library builds a tree structure from spans, allowing assertions on parent-child relationships.

#### Kotlin (DSL)

```kotlin
collector.awaitTraceMatching {
    containsSpanNamed("GreetingService.greet")
}.assertThat {
    rootSpan("GET /greet/{name}") {
        span("GreetingService.greet") {
            hasAttribute("greeting.name", "Darek")
            hasStatusOk()
            span("GreetingService.buildGreeting")
        }
    }
}
```

Use `anySpan` to assert on a span anywhere in the tree without specifying its exact position:

```kotlin
trace.assertThat {
    anySpan("DB query") {
        hasAttribute("db.table", "users")
    }
    anySpan({ it.name.startsWith("HTTP") }) {
        hasStatusOk()
    }
}
```

Assert on multiple spans with the same name using indexed access:

```kotlin
trace.assertThat {
    rootSpan("root") {
        hasSpans("DB query", 2)
        span("DB query")[0] {
            hasAttribute("db.table", "users")
        }
        span("DB query")[1] {
            hasAttribute("db.table", "orders")
        }
    }
}
```

#### Java (Fluent API)

```java
var trace = collector.awaitTrace(t ->
        t.spanNames().contains("GreetingService.greet"));

TraceAssert.assertThat(trace)
        .hasSpanCount(3)
        .hasRootSpan("GET /greet/{name}")
        .spanWithName("GreetingService.buildGreeting")
        .hasParent("GreetingService.greet");
```

## Sample Projects

See the `sample-spring-boot` (Kotlin) and `sample-spring-boot-java` (Java) modules for complete working examples with Spring Boot and the OpenTelemetry Java agent.