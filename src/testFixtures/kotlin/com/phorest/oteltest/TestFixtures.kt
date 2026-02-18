package com.phorest.oteltest

import com.google.protobuf.ByteString
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest
import io.opentelemetry.proto.common.v1.AnyValue
import io.opentelemetry.proto.common.v1.KeyValue
import io.opentelemetry.proto.trace.v1.ResourceSpans
import io.opentelemetry.proto.trace.v1.ScopeSpans
import io.opentelemetry.proto.trace.v1.Span
import java.net.HttpURLConnection
import java.net.URI

object TestFixtures {

    fun span(name: String): Span =
        Span.newBuilder().setName(name).build()

    fun attr(key: String, value: String): KeyValue =
        KeyValue.newBuilder()
            .setKey(key)
            .setValue(AnyValue.newBuilder().setStringValue(value))
            .build()

    fun exportRequest(vararg spans: Span): ExportTraceServiceRequest =
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

    fun sendSpans(port: Int, vararg names: String) {
        val spans = names.map { span(it) }
        sendSpans(port, *spans.toTypedArray())
    }

    fun sendSpans(port: Int, vararg spans: Span) {
        postTraces(port, exportRequest(*spans).toByteArray())
    }

    fun postTraces(port: Int, body: ByteArray): HttpResponse {
        val url = URI("http://localhost:$port/v1/traces").toURL()
        val connection = url.openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
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

    data class HttpResponse(
        val statusCode: Int,
        val contentType: String?,
        val body: ByteArray
    )
}

data class SpanDef(
    val name: String,
    val parent: String?,
    val attributes: Map<String, String> = emptyMap()
)

fun spanDef(name: String, parent: String?, vararg attributes: Pair<String, String>): SpanDef =
    SpanDef(name, parent, attributes.toMap())

class TraceBuilder {
    private val spanIds = mutableMapOf<String, ByteString>()
    private val nameCounts = mutableMapOf<String, Int>()
    private var idCounter = 0

    private fun nextId(): ByteString =
        ByteString.copyFrom(ByteArray(8) { if (it == 0) (++idCounter).toByte() else 0 })

    private fun newSpanId(name: String): ByteString {
        val occurrence = nameCounts.merge(name, 1, Int::plus)!!
        val key = if (occurrence == 1) name else "$name#$occurrence"
        val id = nextId()
        spanIds[key] = id
        if (occurrence == 1) spanIds[name] = id
        return id
    }

    private fun parentSpanId(name: String): ByteString =
        spanIds[name] ?: error("No span named [$name] registered yet")

    fun buildSpans(traceId: ByteString, vararg spans: Pair<String, String?>): List<Span> =
        buildSpans(traceId, spans.map { (name, parent) -> SpanDef(name, parent) })

    fun buildSpans(traceId: ByteString, spans: List<SpanDef>): List<Span> =
        spans.map { def ->
            Span.newBuilder().apply {
                setName(def.name)
                setTraceId(traceId)
                setSpanId(newSpanId(def.name))
                if (def.parent != null) {
                    setParentSpanId(parentSpanId(def.parent))
                }
                def.attributes.forEach { (key, value) ->
                    addAttributes(TestFixtures.attr(key, value))
                }
            }.build()
        }

    fun sendTrace(port: Int, traceId: Int, vararg spans: Pair<String, String?>) {
        reset()
        val traceIdBytes = ByteString.copyFrom(ByteArray(16) { if (it == 0) traceId.toByte() else 0 })
        val protoSpans = buildSpans(traceIdBytes, *spans)
        TestFixtures.sendSpans(port, *protoSpans.toTypedArray())
    }

    fun buildTrace(vararg spans: Pair<String, String?>): List<Span> {
        reset()
        val traceId = ByteString.copyFrom(ByteArray(16) { 1 })
        return buildSpans(traceId, *spans)
    }

    fun buildTrace(spans: List<SpanDef>): List<Span> {
        reset()
        val traceId = ByteString.copyFrom(ByteArray(16) { 1 })
        return buildSpans(traceId, spans)
    }

    private fun reset() {
        spanIds.clear()
        nameCounts.clear()
        idCounter = 0
    }
}