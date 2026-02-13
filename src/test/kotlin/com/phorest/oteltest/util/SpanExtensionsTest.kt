package com.phorest.oteltest.util

import com.google.protobuf.ByteString
import io.opentelemetry.proto.common.v1.AnyValue
import io.opentelemetry.proto.common.v1.KeyValue
import io.opentelemetry.proto.trace.v1.Span
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpanExtensionsTest {

    @Test
    fun `traceIdHex returns hex string`() {
        val traceId = ByteString.copyFrom(ByteArray(16) { it.toByte() })
        val span = Span.newBuilder().setTraceId(traceId).build()

        assertEquals("000102030405060708090a0b0c0d0e0f", span.traceIdHex)
    }

    @Test
    fun `spanIdHex returns hex string`() {
        val spanId = ByteString.copyFrom(ByteArray(8) { (it + 10).toByte() })
        val span = Span.newBuilder().setSpanId(spanId).build()

        assertEquals("0a0b0c0d0e0f1011", span.spanIdHex)
    }

    @Test
    fun `parentSpanIdHex returns hex string`() {
        val parentId = ByteString.copyFrom(ByteArray(8) { 0xFF.toByte() })
        val span = Span.newBuilder().setParentSpanId(parentId).build()

        assertEquals("ffffffffffffffff", span.parentSpanIdHex)
    }

    @Test
    fun `durationNanos computes correctly`() {
        val span = Span.newBuilder()
            .setStartTimeUnixNano(1_000_000_000)
            .setEndTimeUnixNano(2_500_000_000)
            .build()

        assertEquals(1_500_000_000, span.durationNanos)
    }

    @Test
    fun `durationMillis computes correctly`() {
        val span = Span.newBuilder()
            .setStartTimeUnixNano(1_000_000_000)
            .setEndTimeUnixNano(2_500_000_000)
            .build()

        assertEquals(1500, span.durationMillis)
    }

    @Test
    fun `getStringAttribute returns value`() {
        val span = Span.newBuilder()
            .addAttributes(stringAttr("http.method", "GET"))
            .build()

        assertEquals("GET", span.getStringAttribute("http.method"))
    }

    @Test
    fun `getStringAttribute returns null for missing key`() {
        val span = Span.newBuilder().build()

        assertNull(span.getStringAttribute("missing"))
    }

    @Test
    fun `getLongAttribute returns value`() {
        val span = Span.newBuilder()
            .addAttributes(
                KeyValue.newBuilder()
                    .setKey("http.status_code")
                    .setValue(AnyValue.newBuilder().setIntValue(200))
                    .build()
            )
            .build()

        assertEquals(200L, span.getLongAttribute("http.status_code"))
    }

    @Test
    fun `getBoolAttribute returns value`() {
        val span = Span.newBuilder()
            .addAttributes(
                KeyValue.newBuilder()
                    .setKey("error")
                    .setValue(AnyValue.newBuilder().setBoolValue(true))
                    .build()
            )
            .build()

        assertEquals(true, span.getBoolAttribute("error"))
    }

    @Test
    fun `findByName returns matching span`() {
        val spans = listOf(span("a"), span("b"), span("c"))

        assertEquals("b", spans.findByName("b")?.name)
    }

    @Test
    fun `findByName returns null when not found`() {
        val spans = listOf(span("a"))

        assertNull(spans.findByName("missing"))
    }

    @Test
    fun `filterByName returns all matching spans`() {
        val spans = listOf(span("GET"), span("POST"), span("GET"))

        assertEquals(2, spans.filterByName("GET").size)
    }

    @Test
    fun `groupByTraceId groups correctly`() {
        val traceA = ByteString.copyFrom(ByteArray(16) { 1 })
        val traceB = ByteString.copyFrom(ByteArray(16) { 2 })

        val spans = listOf(
            Span.newBuilder().setName("a1").setTraceId(traceA).build(),
            Span.newBuilder().setName("b1").setTraceId(traceB).build(),
            Span.newBuilder().setName("a2").setTraceId(traceA).build()
        )

        val grouped = spans.groupByTraceId()
        assertEquals(2, grouped.size)
        assertEquals(2, grouped.values.first { it.size == 2 }.size)
    }

    @Test
    fun `rootSpans returns spans without parents`() {
        val spans = listOf(
            Span.newBuilder().setName("root").build(),
            Span.newBuilder().setName("child")
                .setParentSpanId(ByteString.copyFrom(ByteArray(8) { 1 }))
                .build()
        )

        val roots = spans.rootSpans()
        assertEquals(1, roots.size)
        assertEquals("root", roots.first().name)
    }

    @Test
    fun `childrenOf returns child spans`() {
        val parentId = ByteString.copyFrom(ByteArray(8) { 1 })
        val parent = Span.newBuilder().setName("parent").setSpanId(parentId).build()
        val child = Span.newBuilder().setName("child").setParentSpanId(parentId).build()
        val other = Span.newBuilder().setName("other").build()

        val children = listOf(parent, child, other).childrenOf(parent)
        assertEquals(1, children.size)
        assertEquals("child", children.first().name)
    }

    @Test
    fun `childrenOf returns empty for leaf span`() {
        val leafId = ByteString.copyFrom(ByteArray(8) { 99.toByte() })
        val leaf = Span.newBuilder().setName("leaf").setSpanId(leafId).build()

        assertTrue(listOf(leaf).childrenOf(leaf).isEmpty())
    }

    // -- helpers --

    private fun span(name: String): Span = Span.newBuilder().setName(name).build()

    private fun stringAttr(key: String, value: String): KeyValue =
        KeyValue.newBuilder()
            .setKey(key)
            .setValue(AnyValue.newBuilder().setStringValue(value))
            .build()
}