package com.phorest.oteltest.dsl

import com.phorest.oteltest.TestFixtures.attr
import com.phorest.oteltest.TestFixtures.sendSpans
import com.phorest.oteltest.TestFixtures.span
import com.phorest.oteltest.collector.OtlpTestCollector
import io.opentelemetry.proto.trace.v1.Span
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SpanQueryDslTest {

    private lateinit var collector: OtlpTestCollector

    @BeforeEach
    fun setUp() {
        collector = OtlpTestCollector.builder().port(0).build().start()
    }

    @AfterEach
    fun tearDown() {
        collector.close()
    }

    @Test
    fun `spans DSL with withName finds matching span`() {
        sendSpans(collector.getPort(), span("GET /api"), span("POST /api"))
        collector.awaitSpans(2)

        val result = collector.spans { withName("GET /api") }
        assertEquals("GET /api", result.first().name)
    }

    @Test
    fun `spans DSL with withNameContaining filters`() {
        sendSpans(collector.getPort(), span("GET /api/users"), span("POST /api/orders"), span("DB SELECT"))
        collector.awaitSpans(3)

        val result = collector.spans { withNameContaining("/api") }
        assertEquals(2, result.count())
    }

    @Test
    fun `spans DSL with withKind filters by kind`() {
        sendSpans(
            collector.getPort(),
            Span.newBuilder().setName("server").setKind(Span.SpanKind.SPAN_KIND_SERVER).build(),
            Span.newBuilder().setName("client").setKind(Span.SpanKind.SPAN_KIND_CLIENT).build()
        )
        collector.awaitSpans(2)

        val result = collector.spans { withKind(Span.SpanKind.SPAN_KIND_SERVER) }
        assertEquals(1, result.count())
        assertEquals("server", result.first().name)
    }

    @Test
    fun `spans DSL with withAttribute key and value`() {
        sendSpans(
            collector.getPort(),
            Span.newBuilder().setName("db").addAttributes(attr("db.system", "postgresql")).build(),
            span("other")
        )
        collector.awaitSpans(2)

        val result = collector.spans { withAttribute("db.system", "postgresql") }
        assertEquals("db", result.single().name)
    }

    @Test
    fun `spans DSL with withAttribute key only`() {
        sendSpans(
            collector.getPort(),
            Span.newBuilder().setName("db").addAttributes(attr("db.system", "postgresql")).build(),
            span("other")
        )
        collector.awaitSpans(2)

        val result = collector.spans { withAttribute("db.system") }
        assertEquals(1, result.count())
    }

    @Test
    fun `spans DSL combining multiple predicates`() {
        sendSpans(
            collector.getPort(),
            Span.newBuilder().setName("GET /api")
                .setKind(Span.SpanKind.SPAN_KIND_SERVER)
                .addAttributes(attr("http.method", "GET"))
                .build(),
            Span.newBuilder().setName("GET /api")
                .setKind(Span.SpanKind.SPAN_KIND_CLIENT)
                .build(),
            span("POST /api")
        )
        collector.awaitSpans(3)

        val result = collector.spans {
            withName("GET /api")
            withKind(Span.SpanKind.SPAN_KIND_SERVER)
        }
        assertEquals(1, result.count())
    }

    @Test
    fun `spans DSL all returns matching spans`() {
        sendSpans(collector.getPort(), "a", "b", "a")
        collector.awaitSpans(3)

        val result = collector.spans { withName("a") }
        assertEquals(2, result.all().size)
    }

    @Test
    fun `spans DSL none returns true when no matches`() {
        sendSpans(collector.getPort(), "a", "b")
        collector.awaitSpans(2)

        assertTrue(collector.spans { withName("c") }.none())
    }

    @Test
    fun `spans DSL single throws when multiple matches`() {
        sendSpans(collector.getPort(), "a", "a")
        collector.awaitSpans(2)

        assertThrows<IllegalStateException> {
            collector.spans { withName("a") }.single()
        }
    }

    @Test
    fun `awaitSpanMatching DSL waits for matching span`() {
        sendSpans(collector.getPort(), "find-me")

        val span = collector.awaitSpanMatching {
            withName("find-me")
        }
        assertEquals("find-me", span.name)
    }
}