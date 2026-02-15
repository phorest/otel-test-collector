package com.phorest.oteltest.model

import com.google.protobuf.ByteString
import com.phorest.oteltest.TraceBuilder
import com.phorest.oteltest.assertions.TraceAssert
import com.phorest.oteltest.model.TraceTree
import io.opentelemetry.proto.trace.v1.Span
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
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
        assertEquals(listOf("child1", "child2"), tree.rootSpan.children.map { it.name })
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
        assertTrue(output.contains("root"))
        assertTrue(output.contains("child"))
    }

    @Test
    fun `toPrettyString renders tree with connectors`() {
        val spans = traceBuilder.buildTrace(
            "root" to null,
            "child1" to "root",
            "child2" to "root",
            "grandchild" to "child1"
        )
        val tree = TraceTree.buildFrom(spans)

        val expected = """
            |Trace (4 spans)
            |└── root
            |    ├── child1
            |    │   └── grandchild
            |    └── child2
            |""".trimMargin()

        assertEquals(expected, tree.toPrettyString())
    }

    @Test
    fun `SpanNode toPrettyString renders subtree`() {
        val spans = traceBuilder.buildTrace("root" to null, "child" to "root")
        val tree = TraceTree.buildFrom(spans)

        val expected = """
            |└── root
            |    └── child
            |""".trimMargin()

        assertEquals(expected, tree.rootSpan.toPrettyString())
    }

    @Test
    fun `toPrettyString renders single span`() {
        val spans = traceBuilder.buildTrace("root" to null)
        val tree = TraceTree.buildFrom(spans)

        assertEquals("Trace (1 spans)\n└── root\n", tree.toPrettyString())
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

    @Test
    fun `children are ordered by start time`() {
        val traceId = ByteString.copyFrom(ByteArray(16) { 1 })
        val rootId = ByteString.copyFrom(ByteArray(8) { 1 })
        val child1Id = ByteString.copyFrom(ByteArray(8) { 2 })
        val child2Id = ByteString.copyFrom(ByteArray(8) { 3 })
        val child3Id = ByteString.copyFrom(ByteArray(8) { 4 })

        val spans = listOf(
            Span.newBuilder().setName("root").setTraceId(traceId).setSpanId(rootId)
                .setStartTimeUnixNano(1000).build(),
            Span.newBuilder().setName("late-child").setTraceId(traceId).setSpanId(child1Id)
                .setParentSpanId(rootId).setStartTimeUnixNano(5000).build(),
            Span.newBuilder().setName("early-child").setTraceId(traceId).setSpanId(child2Id)
                .setParentSpanId(rootId).setStartTimeUnixNano(2000).build(),
            Span.newBuilder().setName("middle-child").setTraceId(traceId).setSpanId(child3Id)
                .setParentSpanId(rootId).setStartTimeUnixNano(3000).build()
        )

        val tree = TraceTree.buildFrom(spans)
        assertEquals(
            listOf("early-child", "middle-child", "late-child"),
            tree.rootSpan.children.map { it.name }
        )
    }

    @Test
    fun `SpanNode assertThat returns SpanAssert`() {
        val spans = traceBuilder.buildTrace("root" to null)
        val tree = TraceTree.buildFrom(spans)

        tree.rootSpan.assertThat().hasName("root")
    }

    @Test
    fun `TraceTree assertThat returns TraceAssert`() {
        val spans = traceBuilder.buildTrace("root" to null, "child" to "root")
        val tree = TraceTree.buildFrom(spans)

        tree.assertThat().hasSpanCount(2).hasRootSpan("root")
    }
}