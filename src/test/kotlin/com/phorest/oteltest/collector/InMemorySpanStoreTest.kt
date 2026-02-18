package com.phorest.oteltest.collector

import com.google.protobuf.ByteString
import io.opentelemetry.proto.common.v1.AnyValue
import io.opentelemetry.proto.common.v1.KeyValue
import io.opentelemetry.proto.trace.v1.Span
import org.awaitility.core.ConditionTimeoutException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

class InMemorySpanStoreTest {

    private lateinit var store: InMemorySpanStore

    @BeforeEach
    fun setUp() {
        store = InMemorySpanStore()
    }

    @Test
    fun `add stores a span`() {
        val span = spanWith(name = "GET /api")
        store.add(span)

        assertEquals(1, store.count())
        assertEquals("GET /api", store.getAll().first().name)
    }

    @Test
    fun `addAll stores multiple spans`() {
        val spans = listOf(
            spanWith(name = "span-1"),
            spanWith(name = "span-2"),
            spanWith(name = "span-3")
        )
        store.addAll(spans)

        assertEquals(3, store.count())
    }

    @Test
    fun `getAll returns a defensive copy`() {
        store.add(spanWith(name = "original"))
        val snapshot = store.getAll()

        store.add(spanWith(name = "added-later"))

        assertEquals(1, snapshot.size)
        assertEquals(2, store.count())
    }

    @Test
    fun `reset clears all spans`() {
        store.addAll(listOf(spanWith(name = "a"), spanWith(name = "b")))
        assertEquals(2, store.count())

        store.reset()

        assertEquals(0, store.count())
        assertTrue(store.getAll().isEmpty())
    }

    @Test
    fun `byName filters spans by name`() {
        store.addAll(
            listOf(
                spanWith(name = "GET /api"),
                spanWith(name = "POST /api"),
                spanWith(name = "GET /api")
            )
        )

        val result = store.byName("GET /api")
        assertEquals(2, result.size)
        assertTrue(result.all { it.name == "GET /api" })
    }

    @Test
    fun `byName returns empty list when no match`() {
        store.add(spanWith(name = "GET /api"))
        assertTrue(store.byName("DELETE /api").isEmpty())
    }

    @Test
    fun `byKind filters spans by kind`() {
        store.addAll(
            listOf(
                spanWith(name = "server", kind = Span.SpanKind.SPAN_KIND_SERVER),
                spanWith(name = "client", kind = Span.SpanKind.SPAN_KIND_CLIENT),
                spanWith(name = "server2", kind = Span.SpanKind.SPAN_KIND_SERVER)
            )
        )

        val result = store.byKind(Span.SpanKind.SPAN_KIND_SERVER)
        assertEquals(2, result.size)
    }

    @Test
    fun `byAttribute filters spans by attribute key and value`() {
        store.addAll(
            listOf(
                spanWith(name = "span1", attributes = mapOf("http.method" to "GET")),
                spanWith(name = "span2", attributes = mapOf("http.method" to "POST")),
                spanWith(name = "span3", attributes = mapOf("db.system" to "postgresql"))
            )
        )

        val result = store.byAttribute("http.method", "GET")
        assertEquals(1, result.size)
        assertEquals("span1", result.first().name)
    }

    @Test
    fun `byTraceId filters spans by trace ID`() {
        val traceIdBytes = ByteString.copyFrom(
            byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15)
        )
        store.addAll(
            listOf(
                spanWith(name = "match", traceId = traceIdBytes),
                spanWith(name = "no-match")
            )
        )

        val result = store.byTraceId("000102030405060708090a0b0c0d0e0f")
        assertEquals(1, result.size)
        assertEquals("match", result.first().name)
    }

    @Test
    fun `awaitCount waits until enough spans arrive`() {
        val executor = Executors.newSingleThreadExecutor()
        executor.submit {
            Thread.sleep(100)
            store.add(spanWith(name = "delayed-1"))
            store.add(spanWith(name = "delayed-2"))
        }

        val result = store.awaitCount(count = 2, timeout = Duration.ofSeconds(5))
        assertTrue(result.size >= 2)
        executor.shutdown()
    }

    @Test
    fun `awaitCount throws on timeout`() {
        assertThrows<ConditionTimeoutException> {
            store.awaitCount(count = 5, timeout = Duration.ofMillis(200))
        }
    }

    @Test
    fun `awaitSpan waits until matching span arrives`() {
        val executor = Executors.newSingleThreadExecutor()
        executor.submit {
            Thread.sleep(100)
            store.add(spanWith(name = "not-this"))
            store.add(spanWith(name = "target"))
        }

        val span = store.awaitSpan(timeout = Duration.ofSeconds(5)) { it.name == "target" }
        assertEquals("target", span.name)
        executor.shutdown()
    }

    @Test
    fun `awaitSpan throws on timeout when no match`() {
        store.add(spanWith(name = "wrong"))

        assertThrows<ConditionTimeoutException> {
            store.awaitSpan(timeout = Duration.ofMillis(200)) { it.name == "target" }
        }
    }

    @Test
    fun `concurrent adds are thread-safe`() {
        val threadCount = 10
        val spansPerThread = 100
        val latch = CountDownLatch(threadCount)
        val executor = Executors.newFixedThreadPool(threadCount)

        repeat(threadCount) { threadIndex ->
            executor.submit {
                repeat(spansPerThread) { spanIndex ->
                    store.add(spanWith(name = "thread-$threadIndex-span-$spanIndex"))
                }
                latch.countDown()
            }
        }

        latch.await()
        assertEquals(threadCount * spansPerThread, store.count())
        executor.shutdown()
    }

    private fun spanWith(
        name: String = "test-span",
        kind: Span.SpanKind = Span.SpanKind.SPAN_KIND_INTERNAL,
        attributes: Map<String, String> = emptyMap(),
        traceId: ByteString = ByteString.EMPTY
    ): Span {
        val builder = Span.newBuilder()
            .setName(name)
            .setKind(kind)
            .setTraceId(traceId)

        attributes.forEach { (key, value) ->
            builder.addAttributes(
                KeyValue.newBuilder()
                    .setKey(key)
                    .setValue(AnyValue.newBuilder().setStringValue(value))
            )
        }

        return builder.build()
    }
}