package com.phorest.oteltest.assertions

import io.opentelemetry.proto.trace.v1.Span

class TraceAssert private constructor(private val spans: List<Span>) {

    private val spansByParentId: Map<String, List<Span>> by lazy {
        spans.groupBy { it.parentSpanId.toHexString() }
    }

    fun hasSpanCount(expected: Int): TraceAssert = apply {
        assert(spans.size == expected) {
            "Expected trace to have [$expected] spans but had [${spans.size}]"
        }
    }

    fun hasRootSpan(name: String): TraceAssert = apply {
        val roots = spans.filter { it.parentSpanId.isEmpty }
        assert(roots.any { it.name == name }) {
            val rootNames = roots.map { it.name }
            "Expected root span named [$name] but root spans were: $rootNames"
        }
    }

    fun hasDepth(expected: Int): TraceAssert = apply {
        val roots = spans.filter { it.parentSpanId.isEmpty }
        assert(roots.isNotEmpty()) {
            "Cannot compute depth: no root spans found in trace"
        }
        val actual = roots.maxOf { computeDepth(it) }
        assert(actual == expected) {
            "Expected trace depth to be [$expected] but was [$actual]"
        }
    }

    fun spanWithName(name: String): SpanInTraceAssert {
        val span = spans.find { it.name == name }
            ?: throw AssertionError(
                "Expected span with name [$name] in trace but found: ${spans.map { it.name }}"
            )
        return SpanInTraceAssert(span, spans)
    }

    fun spansAreOrdered(vararg names: String): TraceAssert = apply {
        val spanNames = spans.map { it.name }
        val indices = names.map { name ->
            val index = spanNames.indexOf(name)
            assert(index >= 0) {
                "Expected span [$name] in trace but found: $spanNames"
            }
            index
        }
        val isSorted = indices.zipWithNext().all { (a, b) -> a < b }
        assert(isSorted) {
            "Expected spans to be ordered as ${names.toList()} but actual order was $spanNames"
        }
    }

    private fun computeDepth(span: Span): Int {
        val children = spansByParentId[span.spanId.toHexString()] ?: return 1
        return 1 + (children.maxOfOrNull { computeDepth(it) } ?: 0)
    }

    companion object {
        fun assertThat(spans: List<Span>): TraceAssert = TraceAssert(spans)
    }
}

class SpanInTraceAssert(private val span: Span, private val allSpans: List<Span>) {

    fun hasParent(parentName: String): SpanInTraceAssert = apply {
        val parentSpanId = span.parentSpanId.toHexString()
        val parent = allSpans.find { it.spanId.toHexString() == parentSpanId }
            ?: throw AssertionError(
                "Expected span [${span.name}] to have a parent but parent span ID [$parentSpanId] was not found in trace"
            )
        if (parent.name != parentName) {
            throw AssertionError(
                "Expected span [${span.name}] to have parent [$parentName] but parent was [${parent.name}]"
            )
        }
    }

    fun hasNoParent(): SpanInTraceAssert = apply {
        if (!span.parentSpanId.isEmpty) {
            throw AssertionError(
                "Expected span [${span.name}] to have no parent but had parent span ID [${span.parentSpanId.toHexString()}]"
            )
        }
    }
}

fun List<Span>.asTrace(): TraceAssert = TraceAssert.assertThat(this)

private fun assert(condition: Boolean, message: () -> String) {
    if (!condition) throw AssertionError(message())
}

private fun com.google.protobuf.ByteString.toHexString(): String =
    toByteArray().joinToString("") { "%02x".format(it) }