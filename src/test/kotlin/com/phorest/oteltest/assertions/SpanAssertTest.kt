package com.phorest.oteltest.assertions

import com.google.protobuf.ByteString
import com.phorest.oteltest.TestFixtures.attr
import com.phorest.oteltest.TestFixtures.span
import io.opentelemetry.proto.trace.v1.Span
import io.opentelemetry.proto.trace.v1.Status
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration

class SpanAssertTest {

    @Test
    fun `hasName passes for matching name`() {
        SpanAssert.assertThat(span("GET /api"))
            .hasName("GET /api")
    }

    @Test
    fun `hasName fails with descriptive message`() {
        val error = assertThrows<AssertionError> {
            SpanAssert.assertThat(span("POST /api"))
                .hasName("GET /api")
        }
        assert(error.message!!.contains("GET /api"))
        assert(error.message!!.contains("POST /api"))
    }

    @Test
    fun `hasNameMatching passes for matching regex`() {
        SpanAssert.assertThat(span("GET /api/users/123"))
            .hasNameMatching(Regex("GET /api/users/\\d+"))
    }

    @Test
    fun `hasNameMatching fails for non-matching regex`() {
        assertThrows<AssertionError> {
            SpanAssert.assertThat(span("POST /api"))
                .hasNameMatching(Regex("GET .*"))
        }
    }

    @Test
    fun `hasKind passes for matching kind`() {
        val s = Span.newBuilder()
            .setName("test")
            .setKind(Span.SpanKind.SPAN_KIND_SERVER)
            .build()

        SpanAssert.assertThat(s).hasKind(Span.SpanKind.SPAN_KIND_SERVER)
    }

    @Test
    fun `hasKind fails with descriptive message`() {
        val s = Span.newBuilder()
            .setName("test")
            .setKind(Span.SpanKind.SPAN_KIND_CLIENT)
            .build()

        val error = assertThrows<AssertionError> {
            SpanAssert.assertThat(s).hasKind(Span.SpanKind.SPAN_KIND_SERVER)
        }
        assert(error.message!!.contains("SERVER"))
        assert(error.message!!.contains("CLIENT"))
    }

    @Test
    fun `hasAttribute passes for matching attribute`() {
        val s = Span.newBuilder()
            .setName("test")
            .addAttributes(attr("http.method", "GET"))
            .build()

        SpanAssert.assertThat(s).hasAttribute("http.method", "GET")
    }

    @Test
    fun `hasAttribute fails when attribute missing`() {
        val error = assertThrows<AssertionError> {
            SpanAssert.assertThat(span("test"))
                .hasAttribute("http.method", "GET")
        }
        assert(error.message!!.contains("http.method"))
        assert(error.message!!.contains("not present"))
    }

    @Test
    fun `hasAttribute fails when value does not match`() {
        val s = Span.newBuilder()
            .setName("test")
            .addAttributes(attr("http.method", "POST"))
            .build()

        val error = assertThrows<AssertionError> {
            SpanAssert.assertThat(s).hasAttribute("http.method", "GET")
        }
        assert(error.message!!.contains("GET"))
        assert(error.message!!.contains("POST"))
    }

    @Test
    fun `hasAttributeMatching passes for matching regex`() {
        val s = Span.newBuilder()
            .setName("test")
            .addAttributes(attr("http.url", "https://api.example.com/users"))
            .build()

        SpanAssert.assertThat(s).hasAttributeMatching("http.url", Regex("https://.*"))
    }

    @Test
    fun `hasAttributeSatisfying passes for matching predicate`() {
        val s = Span.newBuilder()
            .setName("test")
            .addAttributes(attr("http.status_code", "200"))
            .build()

        SpanAssert.assertThat(s).hasAttributeSatisfying("http.status_code") {
            it.toInt() in 200..299
        }
    }

    @Test
    fun `hasNoParent passes for root span`() {
        SpanAssert.assertThat(span("root")).hasNoParent()
    }

    @Test
    fun `hasNoParent fails for child span`() {
        val s = Span.newBuilder()
            .setName("child")
            .setParentSpanId(ByteString.copyFrom(ByteArray(8) { 1 }))
            .build()

        assertThrows<AssertionError> {
            SpanAssert.assertThat(s).hasNoParent()
        }
    }

    @Test
    fun `hasParentSpanId passes for matching ID`() {
        val parentId = ByteString.copyFrom(ByteArray(8) { it.toByte() })
        val s = Span.newBuilder()
            .setName("child")
            .setParentSpanId(parentId)
            .build()

        val expectedHex = parentId.toByteArray().joinToString("") { "%02x".format(it) }
        SpanAssert.assertThat(s).hasParentSpanId(expectedHex)
    }

    @Test
    fun `hasTraceId passes for matching ID`() {
        val traceId = ByteString.copyFrom(ByteArray(16) { it.toByte() })
        val s = Span.newBuilder()
            .setName("test")
            .setTraceId(traceId)
            .build()

        val expectedHex = traceId.toByteArray().joinToString("") { "%02x".format(it) }
        SpanAssert.assertThat(s).hasTraceId(expectedHex)
    }

    @Test
    fun `hasStatusOk passes for OK status`() {
        val s = Span.newBuilder()
            .setName("test")
            .setStatus(Status.newBuilder().setCode(Status.StatusCode.STATUS_CODE_OK))
            .build()

        SpanAssert.assertThat(s).hasStatusOk()
    }

    @Test
    fun `hasStatusError passes for ERROR status`() {
        val s = Span.newBuilder()
            .setName("test")
            .setStatus(Status.newBuilder().setCode(Status.StatusCode.STATUS_CODE_ERROR))
            .build()

        SpanAssert.assertThat(s).hasStatusError()
    }

    @Test
    fun `hasEvent passes when event exists`() {
        val s = Span.newBuilder()
            .setName("test")
            .addEvents(Span.Event.newBuilder().setName("exception"))
            .build()

        SpanAssert.assertThat(s).hasEvent("exception")
    }

    @Test
    fun `hasEvent fails with descriptive message when event missing`() {
        val error = assertThrows<AssertionError> {
            SpanAssert.assertThat(span("test")).hasEvent("exception")
        }
        assert(error.message!!.contains("exception"))
    }

    @Test
    fun `hasDurationGreaterThan passes for longer span`() {
        val s = Span.newBuilder()
            .setName("test")
            .setStartTimeUnixNano(1_000_000_000)
            .setEndTimeUnixNano(2_000_000_000)
            .build()

        SpanAssert.assertThat(s).hasDurationGreaterThan(Duration.ofMillis(500))
    }

    @Test
    fun `hasDurationLessThan passes for shorter span`() {
        val s = Span.newBuilder()
            .setName("test")
            .setStartTimeUnixNano(1_000_000_000)
            .setEndTimeUnixNano(1_500_000_000)
            .build()

        SpanAssert.assertThat(s).hasDurationLessThan(Duration.ofSeconds(1))
    }

    @Test
    fun `fluent chaining works`() {
        val s = Span.newBuilder()
            .setName("GET /api")
            .setKind(Span.SpanKind.SPAN_KIND_SERVER)
            .addAttributes(attr("http.method", "GET"))
            .setStatus(Status.newBuilder().setCode(Status.StatusCode.STATUS_CODE_OK))
            .build()

        SpanAssert.assertThat(s)
            .hasName("GET /api")
            .hasKind(Span.SpanKind.SPAN_KIND_SERVER)
            .hasAttribute("http.method", "GET")
            .hasStatusOk()
    }
}