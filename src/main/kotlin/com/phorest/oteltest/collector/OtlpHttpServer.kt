package com.phorest.oteltest.collector

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceResponse
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress

internal class OtlpHttpServer(
    private val port: Int = 4318,
    private val spanStore: InMemorySpanStore
) {

    private val logger = LoggerFactory.getLogger(OtlpHttpServer::class.java)
    private var server: HttpServer? = null
    private var resolvedPort: Int = port

    fun start() {
        val httpServer = HttpServer.create(InetSocketAddress(port), 0)
        httpServer.createContext("/v1/traces") { exchange ->
            if (exchange.requestMethod == "POST") {
                handleTraces(exchange)
            } else {
                exchange.sendResponseHeaders(405, -1)
                exchange.close()
            }
        }
        httpServer.start()
        resolvedPort = httpServer.address.port
        server = httpServer
        logger.info("OTLP HTTP server started on port {}", resolvedPort)
    }

    fun stop() {
        server?.stop(1)
        server = null
        logger.info("OTLP HTTP server stopped")
    }

    fun getPort(): Int = resolvedPort

    private fun handleTraces(exchange: HttpExchange) {
        try {
            val bytes = exchange.requestBody.readAllBytes()
            val request = ExportTraceServiceRequest.parseFrom(bytes)

            val spans = request.resourceSpansList.flatMap { resourceSpans ->
                resourceSpans.scopeSpansList.flatMap { scopeSpans ->
                    scopeSpans.spansList
                }
            }

            spanStore.addAll(spans)
            logger.debug("Received {} spans", spans.size)

            val responseBytes = ExportTraceServiceResponse.getDefaultInstance().toByteArray()
            exchange.responseHeaders.set("Content-Type", "application/x-protobuf")
            exchange.sendResponseHeaders(200, responseBytes.size.toLong())
            exchange.responseBody.use { it.write(responseBytes) }
        } catch (e: com.google.protobuf.InvalidProtocolBufferException) {
            logger.warn("Malformed protobuf request", e)
            val msg = "Invalid protobuf payload".toByteArray()
            exchange.sendResponseHeaders(400, msg.size.toLong())
            exchange.responseBody.use { it.write(msg) }
        }
    }
}
