package com.phorest.oteltest.junit5

import com.phorest.oteltest.dsl.SpanQueryBuilder
import com.phorest.oteltest.dsl.SpanQueryResult
import com.phorest.oteltest.dsl.awaitSpanMatching
import com.phorest.oteltest.dsl.spans
import io.opentelemetry.proto.trace.v1.Span
import java.time.Duration

fun OtlpCollectorExtension.spans(block: SpanQueryBuilder.() -> Unit): SpanQueryResult =
    getCollector().spans(block)

fun OtlpCollectorExtension.awaitSpanMatching(
    timeout: Duration = Duration.ofSeconds(10),
    block: SpanQueryBuilder.() -> Unit
): Span = getCollector().awaitSpanMatching(timeout, block)