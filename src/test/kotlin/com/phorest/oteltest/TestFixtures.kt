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

class TraceBuilder {
    private val spanIds = mutableMapOf<String, ByteString>()
    private var idCounter = 0

    private fun spanId(name: String): ByteString =
        spanIds.getOrPut(name) {
            ByteString.copyFrom(ByteArray(8) { if (it == 0) (++idCounter).toByte() else 0 })
        }

    fun buildSpans(traceId: ByteString, vararg spans: Pair<String, String?>): List<Span> =
        spans.map { (name, parentName) ->
            Span.newBuilder().apply {
                setName(name)
                setTraceId(traceId)
                setSpanId(spanId(name))
                if (parentName != null) {
                    setParentSpanId(spanId(parentName))
                }
            }.build()
        }

    fun sendTrace(port: Int, traceId: Int, vararg spans: Pair<String, String?>) {
        spanIds.clear()
        idCounter = 0
        val traceIdBytes = ByteString.copyFrom(ByteArray(16) { if (it == 0) traceId.toByte() else 0 })
        val protoSpans = buildSpans(traceIdBytes, *spans)
        TestFixtures.sendSpans(port, *protoSpans.toTypedArray())
    }

    fun buildTrace(vararg spans: Pair<String, String?>): List<Span> {
        spanIds.clear()
        idCounter = 0
        val traceId = ByteString.copyFrom(ByteArray(16) { 1 })
        return buildSpans(traceId, *spans)
    }
}