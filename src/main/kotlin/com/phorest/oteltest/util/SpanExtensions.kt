package com.phorest.oteltest.util

import io.opentelemetry.proto.trace.v1.Span

val Span.traceIdHex: String
    get() = traceId.toByteArray().toHexString()

val Span.spanIdHex: String
    get() = spanId.toByteArray().toHexString()

val Span.parentSpanIdHex: String
    get() = parentSpanId.toByteArray().toHexString()

val Span.durationNanos: Long
    get() = endTimeUnixNano - startTimeUnixNano

val Span.durationMillis: Long
    get() = durationNanos / 1_000_000

fun Span.getStringAttribute(key: String): String? =
    attributesList.find { it.key == key }?.value?.stringValue

fun Span.getLongAttribute(key: String): Long? =
    attributesList.find { it.key == key }?.value?.intValue

fun Span.getBoolAttribute(key: String): Boolean? =
    attributesList.find { it.key == key }?.value?.boolValue

internal fun List<Span>.findByName(name: String): Span? =
    find { it.name == name }

internal fun List<Span>.filterByName(name: String): List<Span> =
    filter { it.name == name }

internal fun List<Span>.groupByTraceId(): Map<String, List<Span>> =
    groupBy { it.traceIdHex }

internal fun List<Span>.rootSpans(): List<Span> =
    filter { it.parentSpanId.isEmpty }

internal fun List<Span>.childrenOf(span: Span): List<Span> =
    filter { it.parentSpanIdHex == span.spanIdHex }

private fun ByteArray.toHexString(): String =
    joinToString("") { "%02x".format(it) }