package com.phorest.oteltest.dsl

import com.phorest.oteltest.junit5.OtlpCollectorExtension
import com.phorest.oteltest.model.SpanNode
import com.phorest.oteltest.model.TraceTree
import java.time.Duration

fun OtlpCollectorExtension.spans(block: SpanQueryBuilder.() -> Unit): SpanQueryResult =
    getCollector().spans(block)

fun OtlpCollectorExtension.awaitSpanMatching(
    timeout: Duration = Duration.ofSeconds(10),
    block: SpanQueryBuilder.() -> Unit
): SpanNode = getCollector().awaitSpanMatching(timeout, block)

fun OtlpCollectorExtension.awaitTraceMatching(
    timeout: Duration = Duration.ofSeconds(10),
    block: TraceQueryBuilder.() -> Unit
): TraceTree = getCollector().awaitTraceMatching(timeout, block)