package com.phorest.oteltest.assertions

import io.opentelemetry.proto.trace.v1.Span
import io.opentelemetry.proto.trace.v1.Span.Event
import io.opentelemetry.proto.trace.v1.Status
import java.time.Duration
import java.util.function.Consumer

class SpanAssert private constructor(private val span: Span) {

    fun hasName(expected: String): SpanAssert = apply {
        assert(span.name == expected) {
            "Expected span name to be [$expected] but was [${span.name}]"
        }
    }

    fun hasNameMatching(regex: Regex): SpanAssert = apply {
        assert(regex.matches(span.name)) {
            "Expected span name [${span.name}] to match pattern [$regex]"
        }
    }

    fun hasKind(expected: Span.SpanKind): SpanAssert = apply {
        assert(span.kind == expected) {
            "Expected span kind to be [$expected] but was [${span.kind}]"
        }
    }

    fun hasAttribute(key: String, value: String): SpanAssert = apply {
        val attr = span.attributesList.find { it.key == key }
        if (attr == null) {
            fail(
                "Expected span [${span.name}] to have attribute [$key] but it was not present. " +
                    "Available attributes: ${span.attributesList.map { it.key }}"
            )
        }
        assert(attr.value.stringValue == value) {
            "Expected attribute [$key] to be [$value] but was [${attr.value.stringValue}]"
        }
    }

    fun hasAttributeMatching(key: String, regex: Regex): SpanAssert = apply {
        val attr = span.attributesList.find { it.key == key }
            ?: fail("Expected span [${span.name}] to have attribute [$key] but it was not present")
        assert(regex.matches(attr.value.stringValue)) {
            "Expected attribute [$key] value [${attr.value.stringValue}] to match pattern [$regex]"
        }
    }

    fun hasAttributeSatisfying(key: String, predicate: (String) -> Boolean): SpanAssert = apply {
        val attr = span.attributesList.find { it.key == key }
            ?: fail("Expected span [${span.name}] to have attribute [$key] but it was not present")
        assert(predicate(attr.value.stringValue)) {
            "Attribute [$key] with value [${attr.value.stringValue}] did not satisfy the given predicate"
        }
    }

    fun hasNoParent(): SpanAssert = apply {
        assert(span.parentSpanId.isEmpty) {
            "Expected span [${span.name}] to have no parent but had parent span ID [${span.parentSpanId.toHexString()}]"
        }
    }

    fun hasParentSpanId(expected: String): SpanAssert = apply {
        val actual = span.parentSpanId.toHexString()
        assert(actual == expected) {
            "Expected parent span ID to be [$expected] but was [$actual]"
        }
    }

    fun hasTraceId(expected: String): SpanAssert = apply {
        val actual = span.traceId.toHexString()
        assert(actual == expected) {
            "Expected trace ID to be [$expected] but was [$actual]"
        }
    }

    fun hasStatusOk(): SpanAssert = apply {
        assert(span.status.code == Status.StatusCode.STATUS_CODE_OK) {
            "Expected span status to be OK but was [${span.status.code}]"
        }
    }

    fun hasStatusError(): SpanAssert = apply {
        assert(span.status.code == Status.StatusCode.STATUS_CODE_ERROR) {
            "Expected span status to be ERROR but was [${span.status.code}]"
        }
    }

    fun hasEvent(name: String): SpanAssert = apply {
        assert(span.eventsList.any { it.name == name }) {
            "Expected span [${span.name}] to have event [$name] but events were: ${span.eventsList.map { it.name }}"
        }
    }

    @JvmSynthetic
    fun hasEvent(name: String, block: EventAssert.() -> Unit): SpanAssert = apply {
        val event = span.eventsList.find { it.name == name }
            ?: fail("Expected span [${span.name}] to have event [$name] but events were: ${span.eventsList.map { it.name }}")
        EventAssert(event).apply(block)
    }

    fun hasEvent(name: String, block: Consumer<EventAssert>): SpanAssert = apply {
        val event = span.eventsList.find { it.name == name }
            ?: fail("Expected span [${span.name}] to have event [$name] but events were: ${span.eventsList.map { it.name }}")
        block.accept(EventAssert(event))
    }

    fun hasDurationGreaterThan(duration: Duration): SpanAssert = apply {
        val spanDurationNanos = span.endTimeUnixNano - span.startTimeUnixNano
        val expectedNanos = duration.toNanos()
        assert(spanDurationNanos > expectedNanos) {
            "Expected span duration to be greater than ${duration.toMillis()}ms but was ${spanDurationNanos / 1_000_000}ms"
        }
    }

    fun hasDurationLessThan(duration: Duration): SpanAssert = apply {
        val spanDurationNanos = span.endTimeUnixNano - span.startTimeUnixNano
        val expectedNanos = duration.toNanos()
        assert(spanDurationNanos < expectedNanos) {
            "Expected span duration to be less than ${duration.toMillis()}ms but was ${spanDurationNanos / 1_000_000}ms"
        }
    }

    companion object {
        @JvmStatic
        fun assertThat(span: Span): SpanAssert = SpanAssert(span)
    }
}

private fun assert(condition: Boolean, message: () -> String) {
    if (!condition) throw AssertionError(message())
}

private fun fail(message: String): Nothing = throw AssertionError(message)

fun Span.assertThat(): SpanAssert = SpanAssert.assertThat(this)

class EventAssert(private val event: Event) {

    fun hasAttribute(key: String, value: String): EventAssert = apply {
        val attr = event.attributesList.find { it.key == key }
            ?: fail("Expected event [${event.name}] to have attribute [$key] but attributes were: ${event.attributesList.map { it.key }}")
        assert(attr.value.stringValue == value) {
            "Expected event attribute [$key] to be [$value] but was [${attr.value.stringValue}]"
        }
    }

    fun hasAttributeContaining(key: String, substring: String): EventAssert = apply {
        val attr = event.attributesList.find { it.key == key }
            ?: fail("Expected event [${event.name}] to have attribute [$key] but attributes were: ${event.attributesList.map { it.key }}")
        assert(attr.value.stringValue.contains(substring)) {
            "Expected event attribute [$key] to contain [$substring] but was [${attr.value.stringValue}]"
        }
    }
}

private fun com.google.protobuf.ByteString.toHexString(): String =
    toByteArray().joinToString("") { "%02x".format(it) }