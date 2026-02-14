package com.phorest.oteltest.collector

import com.phorest.oteltest.model.SpanNode
import com.phorest.oteltest.model.TraceTree
import com.phorest.oteltest.util.spanIdHex
import com.phorest.oteltest.util.traceIdHex
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

    fun getSpans(): List<SpanNode> = spanStore.getAll().toSpanNodes()

    fun spanCount(): Int = spanStore.count()

    fun awaitSpans(count: Int, timeout: Duration = Duration.ofSeconds(10)): List<SpanNode> =
        spanStore.awaitCount(count, timeout).toSpanNodes()

    fun awaitSpan(
        timeout: Duration = Duration.ofSeconds(10),
        predicate: (SpanNode) -> Boolean
    ): SpanNode {
        val span = spanStore.awaitSpan(timeout) { predicate(it.toSpanNode()) }
        val traceSpans = spanStore.byTraceId(span.traceIdHex)
        if (traceSpans.size > 1) {
            return try {
                val tree = TraceTree.buildFrom(traceSpans)
                tree.findSpanById(span.spanIdHex) ?: span.toSpanNode()
            } catch (_: IllegalStateException) {
                span.toSpanNode()
            }
        }
        return span.toSpanNode()
    }

    fun spansByName(name: String): List<SpanNode> = spanStore.byName(name).toSpanNodes()

    fun spansByKind(kind: Span.SpanKind): List<SpanNode> = spanStore.byKind(kind).toSpanNodes()

    fun spansByAttribute(key: String, value: String): List<SpanNode> = spanStore.byAttribute(key, value).toSpanNodes()

    fun spansByTraceId(traceId: String): List<SpanNode> = spanStore.byTraceId(traceId).toSpanNodes()

    fun traces(): List<TraceTree> =
        spanStore.getAll()
            .groupBy { it.traceIdHex }
            .map { (_, spans) -> TraceTree.buildFrom(spans) }

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

private fun Span.toSpanNode(): SpanNode = SpanNode(this, emptyList())
private fun List<Span>.toSpanNodes(): List<SpanNode> = map { it.toSpanNode() }
