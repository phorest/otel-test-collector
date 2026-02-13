package com.phorest.oteltest.assertions

import com.phorest.oteltest.model.SpanNode
import com.phorest.oteltest.model.TraceTree
import io.opentelemetry.proto.trace.v1.Span
import io.opentelemetry.proto.trace.v1.Status

@DslMarker
annotation class TraceAssertDsl

@TraceAssertDsl
class SpanNodeAssert(private val node: SpanNode) {

    fun hasKind(expected: Span.SpanKind) {
        assert(node.span.kind == expected) {
            "Expected span [${node.name}] kind to be [$expected] but was [${node.span.kind}]"
        }
    }

    fun hasAttribute(key: String, value: String) {
        val attr = node.span.attributesList.find { it.key == key }
            ?: fail(
                "Expected span [${node.name}] to have attribute [$key] but it was not present. " +
                    "Available attributes: ${node.span.attributesList.map { it.key }}"
            )
        assert(attr.value.stringValue == value) {
            "Expected attribute [$key] to be [$value] but was [${attr.value.stringValue}]"
        }
    }

    fun hasStatusOk() {
        assert(node.span.status.code == Status.StatusCode.STATUS_CODE_OK) {
            "Expected span [${node.name}] status to be OK but was [${node.span.status.code}]"
        }
    }

    fun hasStatusError() {
        assert(node.span.status.code == Status.StatusCode.STATUS_CODE_ERROR) {
            "Expected span [${node.name}] status to be ERROR but was [${node.span.status.code}]"
        }
    }

    fun hasEvent(name: String) {
        assert(node.span.eventsList.any { it.name == name }) {
            "Expected span [${node.name}] to have event [$name] but events were: ${node.span.eventsList.map { it.name }}"
        }
    }

    fun hasEvent(name: String, block: EventAssert.() -> Unit) {
        val event = node.span.eventsList.find { it.name == name }
            ?: fail("Expected span [${node.name}] to have event [$name] but events were: ${node.span.eventsList.map { it.name }}")
        EventAssert(event).apply(block)
    }

    fun child(name: String, block: SpanNodeAssert.() -> Unit = {}) {
        val childNode = node.findChild(name)
            ?: throw AssertionError(
                "Expected child span [$name] under [${node.name}] but children were: ${node.children.map { it.name }}"
            )
        SpanNodeAssert(childNode).apply(block)
    }
}

@TraceAssertDsl
class TraceTreeAssertBuilder(private val trace: TraceTree) {

    fun rootSpan(name: String, block: SpanNodeAssert.() -> Unit = {}) {
        assert(trace.rootSpan.name == name) {
            "Expected root span [$name] but was [${trace.rootSpan.name}]"
        }
        SpanNodeAssert(trace.rootSpan).apply(block)
    }
}

private fun assert(condition: Boolean, message: () -> String) {
    if (!condition) throw AssertionError(message())
}

private fun fail(message: String): Nothing = throw AssertionError(message)
