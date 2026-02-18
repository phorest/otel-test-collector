package com.phorest.oteltest.dsl

import com.phorest.oteltest.collector.OtlpTestCollector
import com.phorest.oteltest.model.SpanNode
import com.phorest.oteltest.util.AwaitUtils
import io.opentelemetry.proto.trace.v1.Span
import java.time.Duration

@OtelTestDsl
class SpanQueryBuilder {
    private val predicates = mutableListOf<(SpanNode) -> Boolean>()

    fun withName(name: String) {
        predicates.add { it.name == name }
    }

    fun withNameContaining(substring: String) {
        predicates.add { it.name.contains(substring) }
    }

    fun withKind(kind: Span.SpanKind) {
        predicates.add { it.kind == kind }
    }

    fun withAttribute(key: String, value: String) {
        predicates.add { span ->
            span.attributesList.any { it.key == key && it.value.stringValue == value }
        }
    }

    fun withAttribute(key: String) {
        predicates.add { span ->
            span.attributesList.any { it.key == key }
        }
    }

    fun withTraceId(traceId: String) {
        predicates.add { span ->
            span.span.traceId.toByteArray().joinToString("") { "%02x".format(it) } == traceId
        }
    }

    internal fun matches(span: SpanNode): Boolean = predicates.all { it(span) }

    internal fun first(spans: List<SpanNode>): SpanNode =
        spans.first { matches(it) }

    internal fun single(spans: List<SpanNode>): SpanNode {
        val matching = spans.filter { matches(it) }
        check(matching.size == 1) {
            "Expected exactly 1 span matching query but found ${matching.size}"
        }
        return matching.single()
    }

    internal fun all(spans: List<SpanNode>): List<SpanNode> =
        spans.filter { matches(it) }

    internal fun none(spans: List<SpanNode>): Boolean =
        spans.none { matches(it) }

    internal fun count(spans: List<SpanNode>): Int =
        spans.count { matches(it) }
}

fun OtlpTestCollector.spans(block: SpanQueryBuilder.() -> Unit): SpanQueryResult {
    val builder = SpanQueryBuilder().apply(block)
    return SpanQueryResult(builder, getSpans())
}

fun OtlpTestCollector.awaitSpanMatching(
    timeout: Duration = Duration.ofSeconds(10),
    block: SpanQueryBuilder.() -> Unit
): SpanNode {
    val builder = SpanQueryBuilder().apply(block)
    return AwaitUtils.awaitUntilNotNull(timeout = timeout) {
        getSpans().firstOrNull { builder.matches(it) }
    }
}

class SpanQueryResult(private val builder: SpanQueryBuilder, private val spans: List<SpanNode>) {

    fun first(): SpanNode = builder.first(spans)

    fun single(): SpanNode = builder.single(spans)

    fun all(): List<SpanNode> = builder.all(spans)

    fun none(): Boolean = builder.none(spans)

    fun count(): Int = builder.count(spans)
}