package com.phorest.oteltest.collector

import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest
import io.opentelemetry.proto.common.v1.AnyValue
import io.opentelemetry.proto.common.v1.KeyValue
import io.opentelemetry.proto.trace.v1.ResourceSpans
import io.opentelemetry.proto.trace.v1.ScopeSpans
import io.opentelemetry.proto.trace.v1.Span
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.HttpURLConnection
import java.net.URI
import java.time.Duration

class OtlpTestCollectorTest {

    private var collector: OtlpTestCollector? = null

    @AfterEach
    fun tearDown() {
        collector?.close()
    }

    @Test
    fun `create starts collector with default port`() {
        collector = OtlpTestCollector.create().start()

        assertTrue(collector!!.getPort() > 0)
    }

    @Test
    fun `builder configures custom port`() {
        collector = OtlpTestCollector.builder()
            .port(0)
            .build()
            .start()

        assertTrue(collector!!.getPort() > 0)
    }

    @Test
    fun `random port assignment with port 0`() {
        collector = OtlpTestCollector.builder().port(0).build().start()

        val port = collector!!.getPort()
        assertTrue(port > 0, "Expected random port but got $port")
    }

    @Test
    fun `receives and stores spans via OTLP HTTP`() {
        collector = OtlpTestCollector.builder().port(0).build().start()

        sendSpans(collector!!.getPort(), "test-span-1", "test-span-2")

        val spans = collector!!.awaitSpans(2)
        assertEquals(2, spans.size)
        assertEquals("test-span-1", spans[0].name)
        assertEquals("test-span-2", spans[1].name)
    }

    @Test
    fun `getSpans returns all captured spans`() {
        collector = OtlpTestCollector.builder().port(0).build().start()

        sendSpans(collector!!.getPort(), "span-a", "span-b")
        collector!!.awaitSpans(2)

        assertEquals(2, collector!!.getSpans().size)
    }

    @Test
    fun `spanCount returns number of captured spans`() {
        collector = OtlpTestCollector.builder().port(0).build().start()

        sendSpans(collector!!.getPort(), "a", "b", "c")
        collector!!.awaitSpans(3)

        assertEquals(3, collector!!.spanCount())
    }

    @Test
    fun `reset clears all captured spans`() {
        collector = OtlpTestCollector.builder().port(0).build().start()

        sendSpans(collector!!.getPort(), "before-reset")
        collector!!.awaitSpans(1)

        collector!!.reset()

        assertEquals(0, collector!!.spanCount())
        assertTrue(collector!!.getSpans().isEmpty())
    }

    @Test
    fun `reset allows capturing new spans`() {
        collector = OtlpTestCollector.builder().port(0).build().start()

        sendSpans(collector!!.getPort(), "first")
        collector!!.awaitSpans(1)

        collector!!.reset()

        sendSpans(collector!!.getPort(), "second")
        val spans = collector!!.awaitSpans(1)

        assertEquals(1, spans.size)
        assertEquals("second", spans[0].name)
    }

    @Test
    fun `spansByName filters by span name`() {
        collector = OtlpTestCollector.builder().port(0).build().start()

        sendSpans(collector!!.getPort(), "GET /api", "POST /api", "GET /api")
        collector!!.awaitSpans(3)

        val getSpans = collector!!.spansByName("GET /api")
        assertEquals(2, getSpans.size)
    }

    @Test
    fun `spansByKind filters by span kind`() {
        collector = OtlpTestCollector.builder().port(0).build().start()

        val serverSpan = Span.newBuilder()
            .setName("server")
            .setKind(Span.SpanKind.SPAN_KIND_SERVER)
            .build()
        val clientSpan = Span.newBuilder()
            .setName("client")
            .setKind(Span.SpanKind.SPAN_KIND_CLIENT)
            .build()

        sendSpans(collector!!.getPort(), serverSpan, clientSpan)
        collector!!.awaitSpans(2)

        assertEquals(1, collector!!.spansByKind(Span.SpanKind.SPAN_KIND_SERVER).size)
        assertEquals("server", collector!!.spansByKind(Span.SpanKind.SPAN_KIND_SERVER).first().name)
    }

    @Test
    fun `spansByAttribute filters by attribute key and value`() {
        collector = OtlpTestCollector.builder().port(0).build().start()

        val span = Span.newBuilder()
            .setName("db-query")
            .addAttributes(attr("db.system", "postgresql"))
            .build()

        sendSpans(collector!!.getPort(), span, Span.newBuilder().setName("other").build())
        collector!!.awaitSpans(2)

        val dbSpans = collector!!.spansByAttribute("db.system", "postgresql")
        assertEquals(1, dbSpans.size)
        assertEquals("db-query", dbSpans.first().name)
    }

    @Test
    fun `awaitSpan waits for matching span`() {
        collector = OtlpTestCollector.builder().port(0).build().start()

        sendSpans(collector!!.getPort(), "not-this", "find-me")

        val span = collector!!.awaitSpan { it.name == "find-me" }
        assertEquals("find-me", span.name)
    }

    @Test
    fun `awaitSpans times out when not enough spans`() {
        collector = OtlpTestCollector.builder().port(0).build().start()

        assertThrows<org.awaitility.core.ConditionTimeoutException> {
            collector!!.awaitSpans(count = 5, timeout = Duration.ofMillis(200))
        }
    }

    @Test
    fun `AutoCloseable stops the server`() {
        val port: Int
        OtlpTestCollector.builder().port(0).build().start().use { c ->
            port = c.getPort()
            sendSpans(port, "inside-use")
            c.awaitSpans(1)
            assertEquals(1, c.spanCount())
        }

        // After close, sending should fail
        assertThrows<Exception> {
            sendSpans(port, "after-close")
        }
    }

    // -- helpers --

    private fun sendSpans(port: Int, vararg names: String) {
        val spans = names.map { Span.newBuilder().setName(it).build() }
        sendSpans(port, *spans.toTypedArray())
    }

    private fun sendSpans(port: Int, vararg spans: Span) {
        val request = ExportTraceServiceRequest.newBuilder()
            .addResourceSpans(
                ResourceSpans.newBuilder()
                    .addScopeSpans(
                        ScopeSpans.newBuilder().apply {
                            spans.forEach { addSpans(it) }
                        }
                    )
            )
            .build()

        val url = URI("http://localhost:$port/v1/traces").toURL()
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/x-protobuf")
            connection.outputStream.use { it.write(request.toByteArray()) }
            connection.responseCode // trigger the request
        } finally {
            connection.disconnect()
        }
    }

    private fun attr(key: String, value: String): KeyValue =
        KeyValue.newBuilder()
            .setKey(key)
            .setValue(AnyValue.newBuilder().setStringValue(value))
            .build()
}