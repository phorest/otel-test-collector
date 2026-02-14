package com.phorest.oteltest.model

import com.google.protobuf.ByteString
import com.phorest.oteltest.TraceBuilder
import com.phorest.oteltest.model.TraceTree
import io.opentelemetry.proto.trace.v1.Span
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TraceTreeTest {

    private val traceBuilder = TraceBuilder()

    @Test
    fun `builds tree from flat spans`() {
        val spans = traceBuilder.buildTrace("root" to null, "child" to "root", "grandchild" to "child")
        val tree = TraceTree.buildFrom(spans)

        assertEquals(3, tree.spanCount)
        assertEquals("root", tree.rootSpan.name)
    }

    @Test
    fun `rootSpan returns single root`() {
        val spans = traceBuilder.buildTrace("root" to null, "child" to "root")
        val tree = TraceTree.buildFrom(spans)

        assertEquals("root", tree.rootSpan.name)
    }

    @Test
    fun `buildFrom throws when multiple roots`() {
        val spans = traceBuilder.buildTrace("root1" to null, "root2" to null)

        assertThrows<IllegalStateException> {
            TraceTree.buildFrom(spans)
        }
    }

    @Test
    fun `depth is correct for linear trace`() {
        val spans = traceBuilder.buildTrace("root" to null, "child" to "root", "grandchild" to "child")
        val tree = TraceTree.buildFrom(spans)

        assertEquals(3, tree.depth)
    }

    @Test
    fun `depth is correct for wide trace`() {
        val spans = traceBuilder.buildTrace(
            "root" to null,
            "child1" to "root",
            "child2" to "root",
            "grandchild" to "child2"
        )
        val tree = TraceTree.buildFrom(spans)

        assertEquals(3, tree.depth)
    }

    @Test
    fun `findSpan finds span by name`() {
        val spans = traceBuilder.buildTrace("root" to null, "child" to "root", "grandchild" to "child")
        val tree = TraceTree.buildFrom(spans)

        val found = tree.findSpan("grandchild")
        assertNotNull(found)
        assertEquals("grandchild", found!!.name)
    }

    @Test
    fun `findSpan returns null for missing span`() {
        val spans = traceBuilder.buildTrace("root" to null)
        val tree = TraceTree.buildFrom(spans)

        assertNull(tree.findSpan("missing"))
    }

    @Test
    fun `spanNames returns all span names`() {
        val spans = traceBuilder.buildTrace("root" to null, "child" to "root")
        val tree = TraceTree.buildFrom(spans)

        assertEquals(listOf("root", "child"), tree.spanNames())
    }

    @Test
    fun `SpanNode children are correctly linked`() {
        val spans = traceBuilder.buildTrace("root" to null, "child1" to "root", "child2" to "root")
        val tree = TraceTree.buildFrom(spans)

        assertEquals(2, tree.rootSpan.children.size)
        val childNames = tree.rootSpan.children.map { it.name }.toSet()
        assertEquals(setOf("child1", "child2"), childNames)
    }

    @Test
    fun `SpanNode findChild finds direct child`() {
        val spans = traceBuilder.buildTrace("root" to null, "child" to "root", "grandchild" to "child")
        val tree = TraceTree.buildFrom(spans)

        val child = tree.rootSpan.findChild("child")
        assertNotNull(child)
        assertEquals("child", child!!.name)
    }

    @Test
    fun `SpanNode findChild returns null for non-direct child`() {
        val spans = traceBuilder.buildTrace("root" to null, "child" to "root", "grandchild" to "child")
        val tree = TraceTree.buildFrom(spans)

        assertNull(tree.rootSpan.findChild("grandchild"))
    }

    @Test
    fun `SpanNode findDescendant finds nested span`() {
        val spans = traceBuilder.buildTrace("root" to null, "child" to "root", "grandchild" to "child")
        val tree = TraceTree.buildFrom(spans)

        val found = tree.rootSpan.findDescendant("grandchild")
        assertNotNull(found)
        assertEquals("grandchild", found!!.name)
    }

    @Test
    fun `SpanNode allDescendants returns all nested spans`() {
        val spans = traceBuilder.buildTrace("root" to null, "child" to "root", "gc1" to "child", "gc2" to "child")
        val tree = TraceTree.buildFrom(spans)

        val descendants = tree.rootSpan.allDescendants()
        assertEquals(3, descendants.size)
    }

    @Test
    fun `SpanNode depth is correct`() {
        val spans = traceBuilder.buildTrace("root" to null, "child" to "root", "grandchild" to "child")
        val tree = TraceTree.buildFrom(spans)

        assertEquals(3, tree.rootSpan.depth)
        assertEquals(2, tree.rootSpan.findChild("child")!!.depth)
        assertEquals(1, tree.rootSpan.findDescendant("grandchild")!!.depth)
    }

    @Test
    fun `toString produces readable tree`() {
        val spans = traceBuilder.buildTrace("root" to null, "child" to "root")
        val tree = TraceTree.buildFrom(spans)

        val output = tree.toString()
        assert(output.contains("root"))
        assert(output.contains("child"))
    }

    @Test
    fun `handles orphan spans as roots`() {
        val orphanParentId = ByteString.copyFrom(ByteArray(8) { 0xFF.toByte() })
        val traceId = ByteString.copyFrom(ByteArray(16) { 1 })

        val spans = listOf(
            Span.newBuilder()
                .setName("orphan")
                .setTraceId(traceId)
                .setSpanId(ByteString.copyFrom(ByteArray(8) { 1 }))
                .setParentSpanId(orphanParentId)
                .build()
        )

        val tree = TraceTree.buildFrom(spans)
        assertEquals("orphan", tree.rootSpan.name)
    }
}