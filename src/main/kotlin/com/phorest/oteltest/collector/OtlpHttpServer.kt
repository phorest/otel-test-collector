package com.phorest.oteltest.collector

import io.ktor.http.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceResponse
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

class OtlpHttpServer(
    private val port: Int = 4318,
    private val spanStore: InMemorySpanStore
) {

    private val logger = LoggerFactory.getLogger(OtlpHttpServer::class.java)
    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null
    private var resolvedPort: Int = port

    fun start() {
        server = embeddedServer(Netty, port = port) {
            routing {
                post("/v1/traces") {
                    handleTraces(call)
                }
            }
        }.start(wait = false)

        resolvedPort = if (port == 0) {
            runBlocking { server!!.engine.resolvedConnectors().first().port }
        } else {
            port
        }

        logger.info("OTLP HTTP server started on port {}", resolvedPort)
    }

    fun stop() {
        server?.stop(gracePeriodMillis = 500, timeoutMillis = 1000)
        server = null
        logger.info("OTLP HTTP server stopped")
    }

    fun getPort(): Int = resolvedPort

    private suspend fun handleTraces(call: io.ktor.server.application.ApplicationCall) {
        try {
            val bytes = call.receive<ByteArray>()
            val request = ExportTraceServiceRequest.parseFrom(bytes)

            val spans = request.resourceSpansList.flatMap { resourceSpans ->
                resourceSpans.scopeSpansList.flatMap { scopeSpans ->
                    scopeSpans.spansList
                }
            }

            spanStore.addAll(spans)
            logger.debug("Received {} spans", spans.size)

            call.respondBytes(
                bytes = ExportTraceServiceResponse.getDefaultInstance().toByteArray(),
                contentType = ContentType("application", "x-protobuf"),
                status = HttpStatusCode.OK
            )
        } catch (e: com.google.protobuf.InvalidProtocolBufferException) {
            logger.warn("Malformed protobuf request", e)
            call.respond(HttpStatusCode.BadRequest, "Invalid protobuf payload")
        }
    }
}