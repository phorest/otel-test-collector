package com.phorest.oteltest.dsl

import com.phorest.oteltest.collector.OtlpTestCollector
import com.phorest.oteltest.util.AwaitUtils
import io.opentelemetry.proto.trace.v1.Span
import java.time.Duration

@OtelTestDsl
class SpanQueryBuilder {
    private val predicates = mutableListOf<(Span) -> Boolean>()

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
            span.traceId.toByteArray().joinToString("") { "%02x".format(it) } == traceId
        }
    }

    internal fun matches(span: Span): Boolean = predicates.all { it(span) }

    fun first(spans: List<Span>): Span =
        spans.first { matches(it) }

    fun single(spans: List<Span>): Span {
        val matching = spans.filter { matches(it) }
        check(matching.size == 1) {
            "Expected exactly 1 span matching query but found ${matching.size}"
        }
        return matching.single()
    }

    fun all(spans: List<Span>): List<Span> =
        spans.filter { matches(it) }

    fun none(spans: List<Span>): Boolean =
        spans.none { matches(it) }

    fun count(spans: List<Span>): Int =
        spans.count { matches(it) }
}

fun OtlpTestCollector.spans(block: SpanQueryBuilder.() -> Unit): SpanQueryResult {
    val builder = SpanQueryBuilder().apply(block)
    return SpanQueryResult(builder, getSpans())
}

fun OtlpTestCollector.awaitSpanMatching(
    timeout: Duration = Duration.ofSeconds(10),
    block: SpanQueryBuilder.() -> Unit
): Span {
    val builder = SpanQueryBuilder().apply(block)
    return AwaitUtils.awaitUntilNotNull(timeout = timeout) {
        getSpans().firstOrNull { builder.matches(it) }
    }
}

class SpanQueryResult(private val builder: SpanQueryBuilder, private val spans: List<Span>) {

    fun first(): Span = builder.first(spans)

    fun single(): Span = builder.single(spans)

    fun all(): List<Span> = builder.all(spans)

    fun none(): Boolean = builder.none(spans)

    fun count(): Int = builder.count(spans)
}