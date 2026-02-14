package com.phorest.oteltest.model

import com.phorest.oteltest.assertions.SpanAssert
import com.phorest.oteltest.assertions.TraceAssert
import com.phorest.oteltest.util.spanIdHex
import com.phorest.oteltest.util.parentSpanIdHex
import com.phorest.oteltest.util.traceIdHex
import io.opentelemetry.proto.trace.v1.Span

class SpanNode(
    val span: Span,
    val children: List<SpanNode>
) {
    val name: String get() = span.name
    val spanIdHex: String get() = span.spanIdHex
    val parentSpanIdHex: String get() = span.parentSpanIdHex
    val kind: Span.SpanKind get() = span.kind
    val attributesList get() = span.attributesList

    val depth: Int
        get() = 1 + (children.maxOfOrNull { it.depth } ?: 0)

    fun findChild(name: String): SpanNode? =
        children.find { it.name == name }

    fun findDescendant(name: String): SpanNode? {
        if (this.name == name) return this
        for (child in children) {
            val found = child.findDescendant(name)
            if (found != null) return found
        }
        return null
    }

    fun findDescendantById(spanId: String): SpanNode? {
        if (this.spanIdHex == spanId) return this
        for (child in children) {
            val found = child.findDescendantById(spanId)
            if (found != null) return found
        }
        return null
    }

    fun allDescendants(): List<SpanNode> =
        children + children.flatMap { it.allDescendants() }

    fun assertThat(): SpanAssert = SpanAssert.assertThat(span)

    override fun toString(): String = buildString {
        appendTree(this@SpanNode, indent = 0)
    }

    private fun StringBuilder.appendTree(node: SpanNode, indent: Int) {
        append("  ".repeat(indent))
        append(node.name)
        append(" [${node.spanIdHex.take(8)}]")
        appendLine()
        node.children.forEach { appendTree(it, indent + 1) }
    }
}

class TraceTree(
    val traceId: String,
    val rootSpan: SpanNode,
    val allSpans: List<Span>
) {
    val spanCount: Int get() = allSpans.size

    val depth: Int get() = rootSpan.depth

    fun findSpan(name: String): SpanNode? = rootSpan.findDescendant(name)

    fun findSpanById(spanId: String): SpanNode? = rootSpan.findDescendantById(spanId)

    fun spanNames(): List<String> = allSpans.map { it.name }

    fun assertThat(): TraceAssert = TraceAssert.assertThat(this)

    override fun toString(): String = buildString {
        appendLine("Trace [$traceId] (${allSpans.size} spans, depth $depth)")
        append(rootSpan)
    }

    companion object {
        @JvmStatic
        fun buildFrom(spans: List<Span>): TraceTree {
            require(spans.isNotEmpty()) { "Cannot build trace from empty span list" }

            val traceId = spans.first().traceIdHex
            require(spans.all { it.traceIdHex == traceId }) {
                "Expected all spans to share one traceId but found: ${spans.map { it.traceIdHex }.distinct()}"
            }
            val byParentId = spans.groupBy { it.parentSpanIdHex }
            val spanById = spans.associateBy { it.spanIdHex }

            fun buildNode(span: Span): SpanNode {
                val childSpans = (byParentId[span.spanIdHex] ?: emptyList())
                    .filter { it.spanIdHex != span.spanIdHex }
                    .sortedBy { it.startTimeUnixNano }
                return SpanNode(span, childSpans.map { buildNode(it) })
            }

            val roots = spans
                .filter { it.parentSpanId.isEmpty || spanById[it.parentSpanIdHex] == null }
                .map { buildNode(it) }

            check(roots.size == 1) {
                "Expected single root span but found ${roots.size}: ${roots.map { it.name }}"
            }

            return TraceTree(traceId, roots.single(), spans)
        }
    }
}