package com.phorest.oteltest.dsl

import com.phorest.oteltest.assertions.EventAssert
import com.phorest.oteltest.assertions.SpanAssert
import com.phorest.oteltest.assertions.TraceAssert
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

    fun hasKind(expected: Span.SpanKind) { spanAssert.hasKind(expected) }

    fun hasAttribute(key: String, value: String) { spanAssert.hasAttribute(key, value) }

    fun hasStatusOk() { spanAssert.hasStatusOk() }

    fun hasStatusError() { spanAssert.hasStatusError() }

    fun hasEvent(name: String) { spanAssert.hasEvent(name) }

    fun hasEvent(name: String, block: EventNodeAssert.() -> Unit) {
        spanAssert.hasEvent(name) { EventNodeAssert(this).apply(block) }
    }

    fun child(name: String, block: SpanNodeAssert.() -> Unit = {}) {
        val childNode = node.findChild(name)
            ?: throw AssertionError(
                "Expected child span [$name] under [${node.name}] but children were: ${node.children.map { it.name }}"
            )
        SpanNodeAssert(childNode).apply(block)
    }
}

@OtelTestDsl
class TraceTreeAssertBuilder(private val trace: TraceTree) {

    fun rootSpan(name: String, block: SpanNodeAssert.() -> Unit = {}) {
        if (trace.rootSpan.name != name) {
            throw AssertionError("Expected root span [$name] but was [${trace.rootSpan.name}]")
        }
        SpanNodeAssert(trace.rootSpan).apply(block)
    }
}

fun Span.assertThat(block: SpanNodeAssert.() -> Unit) {
    SpanNodeAssert(SpanNode(this, emptyList())).apply(block)
}

fun TraceTree.assertThat(): TraceAssert = TraceAssert.assertThat(this)

fun TraceTree.assertThat(block: TraceTreeAssertBuilder.() -> Unit) {
    TraceTreeAssertBuilder(this).apply(block)
}