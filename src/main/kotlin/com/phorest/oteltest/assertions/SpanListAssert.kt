package com.phorest.oteltest.assertions

import io.opentelemetry.proto.trace.v1.Span

class SpanListAssert private constructor(private val spans: List<Span>) {

    fun hasSize(expected: Int): SpanListAssert = apply {
        assert(spans.size == expected) {
            "Expected span list size to be [$expected] but was [${spans.size}]"
        }
    }

    fun isEmpty(): SpanListAssert = apply {
        assert(spans.isEmpty()) {
            "Expected span list to be empty but had ${spans.size} spans: ${spans.map { it.name }}"
        }
    }

    fun isNotEmpty(): SpanListAssert = apply {
        assert(spans.isNotEmpty()) {
            "Expected span list to not be empty"
        }
    }

    fun hasSpanWithName(name: String): SpanListAssert = apply {
        assert(spans.any { it.name == name }) {
            "Expected a span with name [$name] but found: ${spans.map { it.name }}"
        }
    }

    fun hasSpanSatisfying(predicate: (Span) -> Boolean): SpanListAssert = apply {
        assert(spans.any(predicate)) {
            "Expected at least one span satisfying the predicate but none matched. Spans: ${spans.map { it.name }}"
        }
    }

    fun allSatisfy(predicate: (Span) -> Boolean): SpanListAssert = apply {
        val failing = spans.filterNot(predicate)
        assert(failing.isEmpty()) {
            "Expected all spans to satisfy the predicate but ${failing.size} did not: ${failing.map { it.name }}"
        }
    }

    fun noneSatisfy(predicate: (Span) -> Boolean): SpanListAssert = apply {
        val matching = spans.filter(predicate)
        assert(matching.isEmpty()) {
            "Expected no spans to satisfy the predicate but ${matching.size} did: ${matching.map { it.name }}"
        }
    }

    fun extractingNames(): List<String> = spans.map { it.name }

    companion object {
        fun assertThat(spans: List<Span>): SpanListAssert = SpanListAssert(spans)
    }
}

private fun assert(condition: Boolean, message: () -> String) {
    if (!condition) throw AssertionError(message())
}