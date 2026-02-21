package com.phorest.oteltest.assertions

import com.phorest.oteltest.model.SpanNode
import com.phorest.oteltest.model.TraceTree
import com.phorest.oteltest.util.parentSpanIdHex
import com.phorest.oteltest.util.spanIdHex
import io.opentelemetry.proto.trace.v1.Span

class TraceAssert private constructor(private val trace: TraceTree) {

    fun hasSpanCount(expected: Int): TraceAssert = apply {
        assert(trace.spanCount == expected) {
            "Expected trace to have [$expected] spans but had [${trace.spanCount}]"
        }
    }

    fun hasRootSpan(name: String): TraceAssert = apply {
        assert(trace.rootSpans.any { it.name == name }) {
            "Expected root span named [$name] but root spans were ${trace.rootSpans.map { it.name }}"
        }
    }

    fun hasDepth(expected: Int): TraceAssert = apply {
        assert(trace.depth == expected) {
            "Expected trace depth to be [$expected] but was [${trace.depth}]"
        }
    }

    fun spanWithName(name: String): SpanInTraceAssert {
        val node = trace.findSpan(name)
            ?: throw AssertionError(
                "Expected span with name [$name] in trace but found: ${trace.spanNames()}"
            )
        return SpanInTraceAssert(node.span, trace.allSpans)
    }

    fun spansAreOrdered(vararg names: String): TraceAssert = apply {
        val spanNames = trace.spanNames()
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

    companion object {
        @JvmStatic
        fun assertThat(trace: TraceTree): TraceAssert = TraceAssert(trace)
    }
}

class SpanInTraceAssert internal constructor(private val span: Span, private val allSpans: List<SpanNode>) {

    fun hasParent(parentName: String): SpanInTraceAssert = apply {
        val parentSpanId = span.parentSpanIdHex
        val parent = allSpans.find { it.span.spanIdHex == parentSpanId }
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
                "Expected span [${span.name}] to have no parent but had parent span ID [${span.parentSpanIdHex}]"
            )
        }
    }
}

fun List<Span>.asTrace(): TraceAssert = TraceAssert.assertThat(TraceTree.buildFrom(this))
