package com.phorest.oteltest.dsl

import com.phorest.oteltest.assertions.EventAssert
import com.phorest.oteltest.assertions.SpanAssert
import com.phorest.oteltest.model.SpanNode
import com.phorest.oteltest.model.TraceTree
import io.opentelemetry.proto.trace.v1.Span

@OtelTestDsl
class EventNodeAssert(private val eventAssert: EventAssert) {

    fun hasAttribute(key: String, value: String) { eventAssert.hasAttribute(key, value) }

    fun hasAttributeContaining(key: String, substring: String) { eventAssert.hasAttributeContaining(key, substring) }
}

@OtelTestDsl
class SpanNodeAssert(private val node: SpanNode) {
    private val spanAssert = SpanAssert.assertThat(node.span)

    operator fun invoke(block: SpanNodeAssert.() -> Unit) = apply(block)

    fun hasKind(expected: Span.SpanKind) { spanAssert.hasKind(expected) }

    fun hasAttribute(key: String, value: String) { spanAssert.hasAttribute(key, value) }

    fun hasStatusOk() { spanAssert.hasStatusOk() }

    fun hasStatusError() { spanAssert.hasStatusError() }

    fun hasEvent(name: String) { spanAssert.hasEvent(name) }

    fun hasEvent(name: String, block: EventNodeAssert.() -> Unit) {
        spanAssert.hasEvent(name) { EventNodeAssert(this).apply(block) }
    }

    fun span(name: String, block: SpanNodeAssert.() -> Unit) {
        val childNode = node.findChild(name)
            ?: throw AssertionError(
                "Expected span [$name] under [${node.name}] but spans were: ${node.children.map { it.name }}"
            )
        SpanNodeAssert(childNode).apply(block)
    }

    fun span(name: String): SpanSelector {
        val matching = node.children.filter { it.name == name }
        if (matching.isEmpty()) {
            throw AssertionError(
                "Expected span [$name] under [${node.name}] but spans were: ${node.children.map { it.name }}"
            )
        }
        return SpanSelector(matching, name, node.name)
    }

    fun hasSpans(name: String, expected: Int) {
        val actual = node.children.count { it.name == name }
        if (actual != expected) {
            throw AssertionError(
                "Expected [$expected] spans named [$name] under [${node.name}] but found [$actual]"
            )
        }
    }
}

@OtelTestDsl
class SpanSelector(private val matching: List<SpanNode>, private val name: String, private val parentName: String) {

    operator fun get(index: Int): SpanNodeAssert {
        if (index >= matching.size) {
            throw AssertionError(
                "Expected span [$name] at index [$index] under [$parentName] " +
                    "but only found [${matching.size}] spans named [$name]"
            )
        }
        return SpanNodeAssert(matching[index])
    }
}

@OtelTestDsl
class TraceTreeAssertBuilder(private val trace: TraceTree) {

    fun rootSpan(name: String, block: SpanNodeAssert.() -> Unit = {}) {
        val root = trace.rootSpans.find { it.name == name }
            ?: throw AssertionError(
                "Expected root span [$name] but root spans were ${trace.rootSpans.map { it.name }}"
            )
        SpanNodeAssert(root).apply(block)
    }

    fun anySpan(name: String, block: SpanNodeAssert.() -> Unit = {}) {
        val node = trace.findSpan(name)
            ?: throw AssertionError(
                "Expected span [$name] anywhere in trace but found: ${trace.spanNames()}"
            )
        SpanNodeAssert(node).apply(block)
    }

    fun anySpan(predicate: (SpanNode) -> Boolean, block: SpanNodeAssert.() -> Unit = {}) {
        val all = trace.rootSpans.flatMap { listOf(it) + it.allDescendants() }
        val node = all.find(predicate)
            ?: throw AssertionError(
                "No span matching predicate in trace. Spans were: ${trace.spanNames()}"
            )
        SpanNodeAssert(node).apply(block)
    }
}

fun SpanNode.assertThat(block: SpanNodeAssert.() -> Unit) {
    SpanNodeAssert(this).apply(block)
}

fun TraceTree.assertThat(block: TraceTreeAssertBuilder.() -> Unit) {
    TraceTreeAssertBuilder(this).apply(block)
}