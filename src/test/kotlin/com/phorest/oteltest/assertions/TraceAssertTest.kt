package com.phorest.oteltest.assertions

import com.phorest.oteltest.TraceBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TraceAssertTest {

    private val traceBuilder = TraceBuilder()

    @Test
    fun `hasSpanCount passes for correct count`() {
        traceBuilder.buildTrace("root" to null, "child" to "root")
            .asTrace()
            .hasSpanCount(2)
    }

    @Test
    fun `hasSpanCount fails with descriptive message`() {
        val error = assertThrows<AssertionError> {
            traceBuilder.buildTrace("root" to null).asTrace().hasSpanCount(3)
        }
        assert(error.message!!.contains("3"))
        assert(error.message!!.contains("1"))
    }

    @Test
    fun `hasRootSpan passes when root span exists`() {
        traceBuilder.buildTrace("my-root" to null, "child" to "my-root")
            .asTrace()
            .hasRootSpan("my-root")
    }

    @Test
    fun `hasRootSpan fails when root span name does not match`() {
        val error = assertThrows<AssertionError> {
            traceBuilder.buildTrace("actual-root" to null).asTrace().hasRootSpan("expected-root")
        }
        assert(error.message!!.contains("expected-root"))
        assert(error.message!!.contains("actual-root"))
    }

    @Test
    fun `hasDepth returns 1 for root-only trace`() {
        traceBuilder.buildTrace("root" to null).asTrace().hasDepth(1)
    }

    @Test
    fun `hasDepth returns correct depth for nested trace`() {
        traceBuilder.buildTrace("root" to null, "child" to "root", "grandchild" to "child")
            .asTrace()
            .hasDepth(3)
    }

    @Test
    fun `hasDepth uses max depth for wide traces`() {
        traceBuilder.buildTrace("root" to null, "child1" to "root", "child2" to "root", "grandchild" to "child2")
            .asTrace()
            .hasDepth(3)
    }

    @Test
    fun `spanWithName finds span and allows parent assertion`() {
        traceBuilder.buildTrace("root" to null, "child" to "root")
            .asTrace()
            .spanWithName("child")
            .hasParent("root")
    }

    @Test
    fun `spanWithName hasNoParent passes for root span`() {
        traceBuilder.buildTrace("root" to null, "child" to "root")
            .asTrace()
            .spanWithName("root")
            .hasNoParent()
    }

    @Test
    fun `spanWithName fails when span not found`() {
        val error = assertThrows<AssertionError> {
            traceBuilder.buildTrace("root" to null).asTrace().spanWithName("missing")
        }
        assert(error.message!!.contains("missing"))
    }

    @Test
    fun `spanWithName hasParent fails when parent name wrong`() {
        val error = assertThrows<AssertionError> {
            traceBuilder.buildTrace("root" to null, "child" to "root")
                .asTrace()
                .spanWithName("child")
                .hasParent("wrong-parent")
        }
        assert(error.message!!.contains("wrong-parent"))
        assert(error.message!!.contains("root"))
    }

    @Test
    fun `spansAreOrdered passes for correct order`() {
        traceBuilder.buildTrace("first" to null, "second" to "first", "third" to "second")
            .asTrace()
            .spansAreOrdered("first", "second", "third")
    }

    @Test
    fun `spansAreOrdered fails for wrong order`() {
        assertThrows<AssertionError> {
            traceBuilder.buildTrace("first" to null, "second" to "first", "third" to "second")
                .asTrace()
                .spansAreOrdered("third", "first")
        }
    }

    @Test
    fun `fluent chaining works`() {
        traceBuilder.buildTrace("root" to null, "child" to "root", "grandchild" to "child")
            .asTrace()
            .hasSpanCount(3)
            .hasRootSpan("root")
            .hasDepth(3)
            .spansAreOrdered("root", "child", "grandchild")
    }
}