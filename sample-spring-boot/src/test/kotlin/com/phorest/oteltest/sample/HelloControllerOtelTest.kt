package com.phorest.oteltest.sample

import com.phorest.oteltest.assertions.SpanAssert.Companion.assertThat
import com.phorest.oteltest.assertions.assertThat
import com.phorest.oteltest.junit5.awaitSpanMatching
import com.phorest.oteltest.junit5.OtlpCollectorExtension
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

        val serverSpan = collector.awaitSpan { span ->
            span.kind == Span.SpanKind.SPAN_KIND_SERVER && span.name.contains("hello")
        }

        assertThat(serverSpan)
            .hasKind(Span.SpanKind.SPAN_KIND_SERVER)
            .hasAttributeSatisfying("http.response.status_code") { it == "200" || it.isEmpty() }
    }

    @Test
    fun `captures HTTP server span using Kotlin DSL`() {
        restTemplate.getForEntity<String>("/hello")

        collector.awaitSpanMatching {
            withKind(Span.SpanKind.SPAN_KIND_SERVER)
            withNameContaining("hello")
        }.assertThat()
            .hasKind(Span.SpanKind.SPAN_KIND_SERVER)
    }
}
