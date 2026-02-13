package com.phorest.oteltest.collector

import io.opentelemetry.proto.trace.v1.Span
import java.time.Duration

class OtlpTestCollector private constructor(
    private val port: Int
) : AutoCloseable {

    private val spanStore = InMemorySpanStore()
    private val server = OtlpHttpServer(port = port, spanStore = spanStore)

    fun start(): OtlpTestCollector {
        server.start()
        return this
    }

    fun stop() {
        server.stop()
    }

    override fun close() {
        stop()
    }

    fun reset() {
        spanStore.reset()
    }

    fun getPort(): Int = server.getPort()

    fun getSpans(): List<Span> = spanStore.getAll()

    fun spanCount(): Int = spanStore.count()

    fun awaitSpans(count: Int, timeout: Duration = Duration.ofSeconds(10)): List<Span> =
        spanStore.awaitCount(count, timeout)

    fun awaitSpan(
        timeout: Duration = Duration.ofSeconds(10),
        predicate: (Span) -> Boolean
    ): Span = spanStore.awaitSpan(timeout, predicate)

    fun spansByName(name: String): List<Span> = spanStore.byName(name)

    fun spansByKind(kind: Span.SpanKind): List<Span> = spanStore.byKind(kind)

    fun spansByAttribute(key: String, value: String): List<Span> = spanStore.byAttribute(key, value)

    fun spansByTraceId(traceId: String): List<Span> = spanStore.byTraceId(traceId)

    companion object {
        private const val DEFAULT_PORT = 4318

        @JvmStatic
        fun create(): OtlpTestCollector = OtlpTestCollector(DEFAULT_PORT)

        @JvmStatic
        fun builder(): Builder = Builder()
    }

    class Builder {
        private var port: Int = DEFAULT_PORT

        fun port(port: Int): Builder = apply { this.port = port }

        fun build(): OtlpTestCollector = OtlpTestCollector(port)
    }
}