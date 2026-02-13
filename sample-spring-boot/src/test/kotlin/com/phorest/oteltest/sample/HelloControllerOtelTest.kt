package com.phorest.oteltest.sample

import com.phorest.oteltest.dsl.assertThat
import com.phorest.oteltest.junit5.OtlpCollectorExtension
import com.phorest.oteltest.dsl.awaitSpanMatching
import com.phorest.oteltest.dsl.awaitTraceMatching
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
        }.assertThat {
            hasKind(Span.SpanKind.SPAN_KIND_SERVER)
        }
    }

    @Test
    fun `captures error span with exception event for failing endpoint`() {
        restTemplate.getForEntity<String>("/fail")

        collector.awaitSpanMatching {
            withKind(Span.SpanKind.SPAN_KIND_SERVER)
            withNameContaining("fail")
        }.assertThat {
            hasKind(Span.SpanKind.SPAN_KIND_SERVER)
            hasStatusError()
            hasEvent("exception") {
                hasAttribute("exception.type", "java.lang.IllegalStateException")
                hasAttribute("exception.message", "Something went wrong")
            }
        }
    }

    @Test
    fun `verifies full trace tree structure for greet endpoint`() {
        restTemplate.getForEntity<String>("/greet/Darek")

        collector.awaitTraceMatching {
            containsSpanNamed("GreetingService.greet")
        }.assertThat {
            rootSpan("GET /greet/{name}") {
                child("GreetingService.greet") {
                    hasAttribute("greeting.name", "Darek")
                    hasStatusOk()
                    child("GreetingService.buildGreeting")
                }
            }
        }
    }
}
