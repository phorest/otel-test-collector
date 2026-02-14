package com.phorest.oteltest.dsl

import com.phorest.oteltest.collector.OtlpTestCollector
import com.phorest.oteltest.model.TraceTree
import com.phorest.oteltest.util.AwaitUtils
import java.time.Duration

@OtelTestDsl
class TraceQueryBuilder {
    private val predicates = mutableListOf<(TraceTree) -> Boolean>()

    fun containsSpanNamed(name: String) {
        predicates.add { trace -> trace.allSpans.any { it.name == name } }
    }

    fun containsSpanMatching(predicate: (io.opentelemetry.proto.trace.v1.Span) -> Boolean) {
        predicates.add { trace -> trace.allSpans.any(predicate) }
    }

    fun hasMinSpanCount(count: Int) {
        predicates.add { trace -> trace.spanCount >= count }
    }

    internal fun matches(trace: TraceTree): Boolean = predicates.all { it(trace) }
}

fun OtlpTestCollector.findTrace(block: TraceQueryBuilder.() -> Unit): TraceTree {
    val builder = TraceQueryBuilder().apply(block)
    val matching = traces().filter { builder.matches(it) }
    check(matching.isNotEmpty()) {
        "No trace found matching query. Available traces: ${traces().map { "trace[${it.traceId.take(8)}](${it.spanNames()})" }}"
    }
    check(matching.size == 1) {
        "Expected 1 trace matching query but found ${matching.size}"
    }
    return matching.single()
}

fun OtlpTestCollector.findTraces(block: TraceQueryBuilder.() -> Unit): List<TraceTree> {
    val builder = TraceQueryBuilder().apply(block)
    return traces().filter { builder.matches(it) }
}

fun OtlpTestCollector.awaitTraceMatching(
    timeout: Duration = Duration.ofSeconds(10),
    block: TraceQueryBuilder.() -> Unit
): TraceTree {
    val builder = TraceQueryBuilder().apply(block)
    return AwaitUtils.awaitUntilNotNull(timeout = timeout) {
        traces().firstOrNull { builder.matches(it) }
    }
}