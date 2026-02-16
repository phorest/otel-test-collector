package com.phorest.oteltest.collector

import com.phorest.oteltest.util.traceIdHex
import io.opentelemetry.proto.trace.v1.Span
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.until
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList

internal class InMemorySpanStore {

    private val spans = CopyOnWriteArrayList<Span>()

    fun add(span: Span) {
        spans.add(span)
    }

    fun addAll(newSpans: List<Span>) {
        spans.addAll(newSpans)
    }

    fun getAll(): List<Span> = spans.toList()

    fun count(): Int = spans.size

    fun reset() {
        spans.clear()
    }

    fun byName(name: String): List<Span> =
        spans.filter { it.name == name }

    fun byKind(kind: Span.SpanKind): List<Span> =
        spans.filter { it.kind == kind }

    fun byAttribute(key: String, value: String): List<Span> =
        spans.filter { span ->
            span.attributesList.any { attr ->
                attr.key == key && attr.value.stringValue == value
            }
        }

    fun byTraceId(traceId: String): List<Span> =
        spans.filter { it.traceIdHex == traceId }

    fun awaitCount(count: Int, timeout: Duration = Duration.ofSeconds(10)): List<Span> {
        await atMost timeout until { spans.size >= count }
        return getAll()
    }

    fun awaitSpan(
        timeout: Duration = Duration.ofSeconds(10),
        predicate: (Span) -> Boolean
    ): Span {
        await atMost timeout until { spans.any(predicate) }
        return spans.first(predicate)
    }
}
