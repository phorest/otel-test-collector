package com.phorest.oteltest.collector

import com.google.protobuf.ByteString
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceResponse
import io.opentelemetry.proto.common.v1.AnyValue
import io.opentelemetry.proto.common.v1.KeyValue
import io.opentelemetry.proto.resource.v1.Resource
import io.opentelemetry.proto.trace.v1.ResourceSpans
import io.opentelemetry.proto.trace.v1.ScopeSpans
import io.opentelemetry.proto.trace.v1.Span
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.HttpURLConnection
import java.net.URI
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
        val request = exportRequest(
            spanWith(name = "GET /api/users"),
            spanWith(name = "DB SELECT users")
        )

        val response = postTraces(request.toByteArray())

        assertEquals(200, response.statusCode)
        assertEquals(2, spanStore.count())
        assertEquals("GET /api/users", spanStore.getAll()[0].name)
        assertEquals("DB SELECT users", spanStore.getAll()[1].name)
    }

    @Test
    fun `POST v1 traces returns valid ExportTraceServiceResponse`() {
        val request = exportRequest(spanWith(name = "test"))

        val response = postTraces(request.toByteArray())

        assertEquals(200, response.statusCode)
        assertEquals("application/x-protobuf", response.contentType)

        val parsed = ExportTraceServiceResponse.parseFrom(response.body)
        // Default instance means empty response with no partial success
        assertEquals(ExportTraceServiceResponse.getDefaultInstance(), parsed)
    }

    @Test
    fun `POST v1 traces preserves span attributes`() {
        val span = Span.newBuilder()
            .setName("HTTP GET")
            .setKind(Span.SpanKind.SPAN_KIND_CLIENT)
            .addAttributes(attr("http.method", "GET"))
            .addAttributes(attr("http.status_code", "200"))
            .build()

        postTraces(exportRequest(span).toByteArray())

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

        val span = Span.newBuilder()
            .setName("traced")
            .setTraceId(traceId)
            .setSpanId(spanId)
            .build()

        postTraces(exportRequest(span).toByteArray())

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
                            .addSpans(spanWith(name = "span-from-service-a"))
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
                            .addSpans(spanWith(name = "span-from-service-b"))
                    )
            )
            .build()

        postTraces(request.toByteArray())

        assertEquals(2, spanStore.count())
        val names = spanStore.getAll().map { it.name }
        assertTrue(names.contains("span-from-service-a"))
        assertTrue(names.contains("span-from-service-b"))
    }

    @Test
    fun `POST v1 traces returns 400 for malformed protobuf`() {
        val response = postTraces("not-valid-protobuf".toByteArray())

        assertEquals(400, response.statusCode)
    }

    @Test
    fun `POST v1 traces handles empty request`() {
        val emptyRequest = ExportTraceServiceRequest.getDefaultInstance()

        val response = postTraces(emptyRequest.toByteArray())

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
                    val request = exportRequest(spanWith(name = "concurrent-$i"))
                    postTraces(request.toByteArray())
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
        postTraces(exportRequest(spanWith(name = "before-restart")).toByteArray())
        assertEquals(1, spanStore.count())

        server.stop()
        server = OtlpHttpServer(port = 0, spanStore = spanStore)
        server.start()

        postTraces(exportRequest(spanWith(name = "after-restart")).toByteArray())
        assertEquals(2, spanStore.count())
    }

    // -- helpers --

    private fun postTraces(body: ByteArray): HttpResponse {
        val url = URI("http://localhost:${server.getPort()}/v1/traces").toURL()
        val connection = url.openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/x-protobuf")
            connection.outputStream.use { it.write(body) }

            val statusCode = connection.responseCode
            val contentType = connection.contentType
            val responseBody = if (statusCode in 200..299) {
                connection.inputStream.use { it.readBytes() }
            } else {
                connection.errorStream?.use { it.readBytes() } ?: ByteArray(0)
            }

            HttpResponse(statusCode, contentType, responseBody)
        } finally {
            connection.disconnect()
        }
    }

    private fun exportRequest(vararg spans: Span): ExportTraceServiceRequest =
        ExportTraceServiceRequest.newBuilder()
            .addResourceSpans(
                ResourceSpans.newBuilder()
                    .addScopeSpans(
                        ScopeSpans.newBuilder().apply {
                            spans.forEach { addSpans(it) }
                        }
                    )
            )
            .build()

    private fun spanWith(name: String): Span =
        Span.newBuilder().setName(name).build()

    private fun attr(key: String, value: String): KeyValue =
        KeyValue.newBuilder()
            .setKey(key)
            .setValue(AnyValue.newBuilder().setStringValue(value))
            .build()

    private data class HttpResponse(
        val statusCode: Int,
        val contentType: String?,
        val body: ByteArray
    )
}