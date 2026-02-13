package com.phorest.oteltest.sample

import com.phorest.oteltest.assertions.assertThat
import com.phorest.oteltest.dsl.assertThat
import com.phorest.oteltest.junit5.OtlpCollectorExtension
import com.phorest.oteltest.junit5.awaitSpanMatching
import com.phorest.oteltest.junit5.awaitTrace
import io.opentelemetry.proto.trace.v1.Span
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.client.getForEntity
import kotlin.test.assertEquals

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
    fun `captures HTTP server span for GET hello`() {
        val response = restTemplate.getForEntity<String>("/hello")
        assertEquals(200, response.statusCode.value())
        assertEquals("Hello, World!", response.body)

        collector.awaitSpanMatching {
            withKind(Span.SpanKind.SPAN_KIND_SERVER)
            withNameContaining("hello")
        }.assertThat()
            .hasKind(Span.SpanKind.SPAN_KIND_SERVER)
    }

    @Test
    fun `captures full trace with custom service spans for greet endpoint`() {
        restTemplate.getForEntity<String>("/greet/Darek")

        collector.awaitTrace {
            containsSpanNamed("GreetingService.greet")
        }.assertThat()
            .hasSpanCount(3)
            .hasRootSpan("GET /greet/{name}")
            .spanWithName("GreetingService.buildGreeting")
            .hasParent("GreetingService.greet")
    }

    @Test
    fun `verifies trace tree structure for greet endpoint`() {
        restTemplate.getForEntity<String>("/greet/Darek")

        val trace = collector.awaitTrace {
            containsSpanNamed("GreetingService.greet")
        }

        assertEquals(3, trace.spanCount)
        assertEquals(3, trace.depth)

        val greetNode = trace.findSpan("GreetingService.greet")!!
        assertEquals(1, greetNode.children.size)
        assertEquals("GreetingService.buildGreeting", greetNode.children.first().name)
    }

    @Test
    fun `verifies root span has expected children`() {
        restTemplate.getForEntity<String>("/greet/Darek")

        val trace = collector.awaitTrace {
            containsSpanNamed("GreetingService.greet")
        }

        assertEquals(1, trace.rootSpan.children.size)
        assertEquals("GreetingService.greet", trace.rootSpan.children.first().name)
    }

    @Test
    fun `verifies custom attributes on greeting service span`() {
        restTemplate.getForEntity<String>("/greet/Darek")

        collector.awaitSpanMatching {
            withName("GreetingService.greet")
            withAttribute("greeting.name", "Darek")
        }.assertThat()
            .hasAttribute("greeting.name", "Darek")
            .hasStatusOk()
    }
}