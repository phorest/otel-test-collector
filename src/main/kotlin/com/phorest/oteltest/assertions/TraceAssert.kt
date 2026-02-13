package com.phorest.oteltest.assertions

import com.google.protobuf.ByteString
import com.phorest.oteltest.model.TraceTree
import io.opentelemetry.proto.trace.v1.Span

class TraceAssert private constructor(private val trace: TraceTree) {

    fun hasSpanCount(expected: Int): TraceAssert = apply {
        assert(trace.spanCount == expected) {
            "Expected trace to have [$expected] spans but had [${trace.spanCount}]"
        }
    }

    fun hasRootSpan(name: String): TraceAssert = apply {
        assert(trace.rootSpan.name == name) {
            "Expected root span named [$name] but was [${trace.rootSpan.name}]"
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

fun TraceTree.asTraceAssert(): TraceAssert = TraceAssert.assertThat(this)

fun List<Span>.asTrace(): TraceAssert = TraceAssert.assertThat(TraceTree.buildFrom(this))

private fun assert(condition: Boolean, message: () -> String) {
    if (!condition) throw AssertionError(message())
}

private fun ByteString.toHexString(): String =
    toByteArray().joinToString("") { "%02x".format(it) }