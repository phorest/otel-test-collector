package com.phorest.oteltest.dsl

import com.phorest.oteltest.TestFixtures.attr
import com.phorest.oteltest.model.SpanNode
import io.opentelemetry.proto.trace.v1.Span
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SpanQueryDslTest {

    private fun span(name: String): SpanNode =
        SpanNode(Span.newBuilder().setName(name).build(), emptyList())

    private fun span(proto: Span): SpanNode = SpanNode(proto, emptyList())

    private fun query(block: SpanQueryBuilder.() -> Unit): SpanQueryBuilder =
        SpanQueryBuilder().apply(block)

    private fun queryResult(spans: List<SpanNode>, block: SpanQueryBuilder.() -> Unit): SpanQueryResult {
        val builder = query(block)
        return SpanQueryResult(builder, spans)
    }

    @Nested
    inner class PredicateTest {

        @Test
        fun `withName matches span by exact name`() {
            val spans = listOf(span("GET /api"), span("POST /api"))
            val result = queryResult(spans) { withName("GET /api") }
            assertEquals("GET /api", result.first().name)
        }

        @Test
        fun `withNameContaining filters by substring`() {
            val spans = listOf(span("GET /api/users"), span("POST /api/orders"), span("DB SELECT"))
            val result = queryResult(spans) { withNameContaining("/api") }
            assertEquals(2, result.count())
        }

        @Test
        fun `withKind filters by span kind`() {
            val spans = listOf(
                span(Span.newBuilder().setName("server").setKind(Span.SpanKind.SPAN_KIND_SERVER).build()),
                span(Span.newBuilder().setName("client").setKind(Span.SpanKind.SPAN_KIND_CLIENT).build())
            )
            val result = queryResult(spans) { withKind(Span.SpanKind.SPAN_KIND_SERVER) }
            assertEquals(1, result.count())
            assertEquals("server", result.first().name)
        }

        @Test
        fun `withAttribute matches key and value`() {
            val spans = listOf(
                span(Span.newBuilder().setName("db").addAttributes(attr("db.system", "postgresql")).build()),
                span("other")
            )
            val result = queryResult(spans) { withAttribute("db.system", "postgresql") }
            assertEquals("db", result.single().name)
        }

        @Test
        fun `withAttribute matches key only`() {
            val spans = listOf(
                span(Span.newBuilder().setName("db").addAttributes(attr("db.system", "postgresql")).build()),
                span("other")
            )
            val result = queryResult(spans) { withAttribute("db.system") }
            assertEquals(1, result.count())
        }

        @Test
        fun `multiple predicates are combined with AND`() {
            val spans = listOf(
                span(Span.newBuilder().setName("GET /api").setKind(Span.SpanKind.SPAN_KIND_SERVER)
                    .addAttributes(attr("http.method", "GET")).build()),
                span(Span.newBuilder().setName("GET /api").setKind(Span.SpanKind.SPAN_KIND_CLIENT).build()),
                span("POST /api")
            )
            val result = queryResult(spans) {
                withName("GET /api")
                withKind(Span.SpanKind.SPAN_KIND_SERVER)
            }
            assertEquals(1, result.count())
        }
    }

    @Nested
    inner class QueryResultTest {

        @Test
        fun `all returns matching spans`() {
            val spans = listOf(span("a"), span("b"), span("a"))
            val result = queryResult(spans) { withName("a") }
            assertEquals(2, result.all().size)
        }

        @Test
        fun `none returns true when no matches`() {
            val spans = listOf(span("a"), span("b"))
            assertTrue(queryResult(spans) { withName("c") }.none())
        }

        @Test
        fun `single throws when multiple matches`() {
            val spans = listOf(span("a"), span("a"))
            assertThrows<IllegalStateException> {
                queryResult(spans) { withName("a") }.single()
            }
        }
    }
}