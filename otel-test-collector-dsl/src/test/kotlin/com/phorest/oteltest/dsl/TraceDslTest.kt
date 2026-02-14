package com.phorest.oteltest.dsl

import com.phorest.oteltest.TraceBuilder
import com.phorest.oteltest.collector.OtlpTestCollector
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TraceDslTest {

    private lateinit var collector: OtlpTestCollector
    private val traceBuilder = TraceBuilder()

    @BeforeEach
    fun setUp() {
        collector = OtlpTestCollector.builder().port(0).build().start()
    }

    @AfterEach
    fun tearDown() {
        collector.close()
    }

    @Test
    fun `traces returns all traces as trees`() {
        traceBuilder.sendTrace(collector.getPort(), traceId = 1, "root-a" to null, "child-a" to "root-a")
        traceBuilder.sendTrace(collector.getPort(), traceId = 2, "root-b" to null)
        collector.awaitSpans(3)

        val traces = collector.traces()
        assertEquals(2, traces.size)
    }

    @Test
    fun `traces builds correct tree structure`() {
        traceBuilder.sendTrace(collector.getPort(), traceId = 1, "root" to null, "child" to "root", "grandchild" to "child")
        collector.awaitSpans(3)

        val trace = collector.traces().first()
        assertEquals("root", trace.rootSpan.name)
        assertEquals("child", trace.rootSpan.findChild("child")!!.name)
        assertEquals("grandchild", trace.rootSpan.findDescendant("grandchild")!!.name)
    }

    @Test
    fun `findTrace finds trace containing named span`() {
        traceBuilder.sendTrace(collector.getPort(), traceId = 1, "GET /api" to null, "DB query" to "GET /api")
        traceBuilder.sendTrace(collector.getPort(), traceId = 2, "POST /orders" to null)
        collector.awaitSpans(3)

        val trace = collector.findTrace { containsSpanNamed("DB query") }
        assertEquals(2, trace.spanCount)
        assertNotNull(trace.findSpan("GET /api"))
        assertNotNull(trace.findSpan("DB query"))
    }

    @Test
    fun `findTrace throws when no trace matches`() {
        traceBuilder.sendTrace(collector.getPort(), traceId = 1, "root" to null)
        collector.awaitSpans(1)

        assertThrows<IllegalStateException> {
            collector.findTrace { containsSpanNamed("missing") }
        }
    }

    @Test
    fun `findTrace throws when multiple traces match`() {
        traceBuilder.sendTrace(collector.getPort(), traceId = 1, "GET /api" to null)
        traceBuilder.sendTrace(collector.getPort(), traceId = 2, "GET /api" to null)
        collector.awaitSpans(2)

        assertThrows<IllegalStateException> {
            collector.findTrace { containsSpanNamed("GET /api") }
        }
    }

    @Test
    fun `findTraces returns all matching traces`() {
        traceBuilder.sendTrace(collector.getPort(), traceId = 1, "GET /api" to null)
        traceBuilder.sendTrace(collector.getPort(), traceId = 2, "GET /api" to null)
        traceBuilder.sendTrace(collector.getPort(), traceId = 3, "POST /orders" to null)
        collector.awaitSpans(3)

        val traces = collector.findTraces { containsSpanNamed("GET /api") }
        assertEquals(2, traces.size)
    }

    @Test
    fun `findTrace with hasMinSpanCount`() {
        traceBuilder.sendTrace(collector.getPort(), traceId = 1, "small" to null)
        traceBuilder.sendTrace(collector.getPort(), traceId = 2, "root" to null, "child" to "root", "grandchild" to "child")
        collector.awaitSpans(4)

        val trace = collector.findTrace { hasMinSpanCount(2) }
        assertEquals(3, trace.spanCount)
    }

    @Test
    fun `findTrace with containsSpanMatching`() {
        traceBuilder.sendTrace(collector.getPort(), traceId = 1, "GET /api" to null)
        traceBuilder.sendTrace(collector.getPort(), traceId = 2, "POST /orders" to null)
        collector.awaitSpans(2)

        val trace = collector.findTrace {
            containsSpanMatching { it.name.startsWith("POST") }
        }
        assertNotNull(trace.findSpan("POST /orders"))
    }

    @Test
    fun `awaitTrace waits for matching trace`() {
        traceBuilder.sendTrace(collector.getPort(), traceId = 1, "GET /api" to null, "DB query" to "GET /api")

        val trace = collector.awaitTraceMatching {
            containsSpanNamed("DB query")
        }
        assertEquals(2, trace.spanCount)
    }

    @Test
    fun `trace tree allows navigating parent-child relationships`() {
        traceBuilder.sendTrace(
            collector.getPort(),
            traceId = 1,
            "HTTP server" to null,
            "GET /api/users" to "HTTP server",
            "DB SELECT users" to "GET /api/users",
            "Redis cache check" to "GET /api/users"
        )
        collector.awaitSpans(4)

        val trace = collector.findTrace { containsSpanNamed("HTTP server") }

        assertEquals("HTTP server", trace.rootSpan.name)
        assertEquals(3, trace.depth)

        val apiSpan = trace.rootSpan.findChild("GET /api/users")!!
        assertEquals(2, apiSpan.children.size)

        val childNames = apiSpan.children.map { it.name }.toSet()
        assertEquals(setOf("DB SELECT users", "Redis cache check"), childNames)
    }
}