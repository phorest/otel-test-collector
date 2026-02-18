package com.phorest.oteltest.util

import com.google.protobuf.ByteString
import io.opentelemetry.proto.trace.v1.Span

internal fun ByteString.toHexString(): String =
    toByteArray().toHexString()

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

private fun ByteArray.toHexString(): String =
    joinToString("") { "%02x".format(it) }