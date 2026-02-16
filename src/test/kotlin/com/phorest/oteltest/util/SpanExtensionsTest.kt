package com.phorest.oteltest.util

import com.google.protobuf.ByteString
import io.opentelemetry.proto.common.v1.AnyValue
import io.opentelemetry.proto.common.v1.KeyValue
import io.opentelemetry.proto.trace.v1.Span
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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

    // -- helpers --

    private fun stringAttr(key: String, value: String): KeyValue =
        KeyValue.newBuilder()
            .setKey(key)
            .setValue(AnyValue.newBuilder().setStringValue(value))
            .build()
}
