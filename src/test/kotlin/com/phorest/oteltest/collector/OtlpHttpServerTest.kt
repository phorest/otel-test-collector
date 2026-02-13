package com.phorest.oteltest.collector

import com.google.protobuf.ByteString
import com.phorest.oteltest.TestFixtures.attr
import com.phorest.oteltest.TestFixtures.exportRequest
import com.phorest.oteltest.TestFixtures.postTraces
import com.phorest.oteltest.TestFixtures.span
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceResponse
import io.opentelemetry.proto.resource.v1.Resource
import io.opentelemetry.proto.trace.v1.ResourceSpans
import io.opentelemetry.proto.trace.v1.ScopeSpans
import io.opentelemetry.proto.trace.v1.Span
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

class OtlpHttpServerTest {

    private lateinit var spanStore: InMemorySpanStore
    private lateinit var server: OtlpHttpServer

    @BeforeEach
    fun setUp() {
        spanStore = InMemorySpanStore()
        server = OtlpHttpServer(port = 0, spanStore = spanStore)
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.stop()
    }

    @Test
    fun `server starts on random port and exposes port`() {
        val port = server.getPort()
        assertTrue(port > 0, "Expected a valid port but got $port")
    }

    @Test
    fun `POST v1 traces parses protobuf and stores spans`() {
        val request = exportRequest(span("GET /api/users"), span("DB SELECT users"))

        val response = postTraces(server.getPort(), request.toByteArray())

        assertEquals(200, response.statusCode)
        assertEquals(2, spanStore.count())
        assertEquals("GET /api/users", spanStore.getAll()[0].name)
        assertEquals("DB SELECT users", spanStore.getAll()[1].name)
    }

    @Test
    fun `POST v1 traces returns valid ExportTraceServiceResponse`() {
        val request = exportRequest(span("test"))

        val response = postTraces(server.getPort(), request.toByteArray())

        assertEquals(200, response.statusCode)
        assertEquals("application/x-protobuf", response.contentType)

        val parsed = ExportTraceServiceResponse.parseFrom(response.body)
        assertEquals(ExportTraceServiceResponse.getDefaultInstance(), parsed)
    }

    @Test
    fun `POST v1 traces preserves span attributes`() {
        val s = Span.newBuilder()
            .setName("HTTP GET")
            .setKind(Span.SpanKind.SPAN_KIND_CLIENT)
            .addAttributes(attr("http.method", "GET"))
            .addAttributes(attr("http.status_code", "200"))
            .build()

        postTraces(server.getPort(), exportRequest(s).toByteArray())

        val stored = spanStore.getAll().first()
        assertEquals("HTTP GET", stored.name)
        assertEquals(Span.SpanKind.SPAN_KIND_CLIENT, stored.kind)

        val attrs = stored.attributesList.associate { it.key to it.value.stringValue }
        assertEquals("GET", attrs["http.method"])
        assertEquals("200", attrs["http.status_code"])
    }

    @Test
    fun `POST v1 traces preserves trace and span IDs`() {
        val traceId = ByteString.copyFrom(ByteArray(16) { it.toByte() })
        val spanId = ByteString.copyFrom(ByteArray(8) { (it + 10).toByte() })

        val s = Span.newBuilder()
            .setName("traced")
            .setTraceId(traceId)
            .setSpanId(spanId)
            .build()

        postTraces(server.getPort(), exportRequest(s).toByteArray())

        val stored = spanStore.getAll().first()
        assertEquals(traceId, stored.traceId)
        assertEquals(spanId, stored.spanId)
    }

    @Test
    fun `POST v1 traces handles multiple ResourceSpans`() {
        val request = ExportTraceServiceRequest.newBuilder()
            .addResourceSpans(
                ResourceSpans.newBuilder()
                    .setResource(
                        Resource.newBuilder()
                            .addAttributes(attr("service.name", "service-a"))
                    )
                    .addScopeSpans(
                        ScopeSpans.newBuilder()
                            .addSpans(span("span-from-service-a"))
                    )
            )
            .addResourceSpans(
                ResourceSpans.newBuilder()
                    .setResource(
                        Resource.newBuilder()
                            .addAttributes(attr("service.name", "service-b"))
                    )
                    .addScopeSpans(
                        ScopeSpans.newBuilder()
                            .addSpans(span("span-from-service-b"))
                    )
            )
            .build()

        postTraces(server.getPort(), request.toByteArray())

        assertEquals(2, spanStore.count())
        val names = spanStore.getAll().map { it.name }
        assertTrue(names.contains("span-from-service-a"))
        assertTrue(names.contains("span-from-service-b"))
    }

    @Test
    fun `POST v1 traces returns 400 for malformed protobuf`() {
        val response = postTraces(server.getPort(), "not-valid-protobuf".toByteArray())

        assertEquals(400, response.statusCode)
    }

    @Test
    fun `POST v1 traces handles empty request`() {
        val emptyRequest = ExportTraceServiceRequest.getDefaultInstance()

        val response = postTraces(server.getPort(), emptyRequest.toByteArray())

        assertEquals(200, response.statusCode)
        assertEquals(0, spanStore.count())
    }

    @Test
    fun `server handles concurrent requests`() {
        val threadCount = 10
        val latch = CountDownLatch(threadCount)
        val executor = Executors.newFixedThreadPool(threadCount)

        repeat(threadCount) { i ->
            executor.submit {
                try {
                    val request = exportRequest(span("concurrent-$i"))
                    postTraces(server.getPort(), request.toByteArray())
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        assertEquals(threadCount, spanStore.count())
        executor.shutdown()
    }

    @Test
    fun `server stop and restart works`() {
        postTraces(server.getPort(), exportRequest(span("before-restart")).toByteArray())
        assertEquals(1, spanStore.count())

        server.stop()
        server = OtlpHttpServer(port = 0, spanStore = spanStore)
        server.start()

        postTraces(server.getPort(), exportRequest(span("after-restart")).toByteArray())
        assertEquals(2, spanStore.count())
    }
}