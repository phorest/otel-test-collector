package com.phorest.oteltest.assertions

import io.opentelemetry.proto.trace.v1.Span
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SpanListAssertTest {

    @Test
    fun `hasSize passes for correct size`() {
        SpanListAssert.assertThat(spans("a", "b", "c")).hasSize(3)
    }

    @Test
    fun `hasSize fails with descriptive message`() {
        val error = assertThrows<AssertionError> {
            SpanListAssert.assertThat(spans("a")).hasSize(3)
        }
        assert(error.message!!.contains("3"))
        assert(error.message!!.contains("1"))
    }

    @Test
    fun `isEmpty passes for empty list`() {
        SpanListAssert.assertThat(emptyList()).isEmpty()
    }

    @Test
    fun `isEmpty fails for non-empty list`() {
        assertThrows<AssertionError> {
            SpanListAssert.assertThat(spans("a")).isEmpty()
        }
    }

    @Test
    fun `isNotEmpty passes for non-empty list`() {
        SpanListAssert.assertThat(spans("a")).isNotEmpty()
    }

    @Test
    fun `isNotEmpty fails for empty list`() {
        assertThrows<AssertionError> {
            SpanListAssert.assertThat(emptyList()).isNotEmpty()
        }
    }

    @Test
    fun `hasSpanWithName passes when span exists`() {
        SpanListAssert.assertThat(spans("GET /api", "POST /api"))
            .hasSpanWithName("GET /api")
    }

    @Test
    fun `hasSpanWithName fails with available names`() {
        val error = assertThrows<AssertionError> {
            SpanListAssert.assertThat(spans("POST /api"))
                .hasSpanWithName("GET /api")
        }
        assert(error.message!!.contains("GET /api"))
        assert(error.message!!.contains("POST /api"))
    }

    @Test
    fun `hasSpanSatisfying passes when predicate matches`() {
        SpanListAssert.assertThat(spans("a", "b"))
            .hasSpanSatisfying { it.name == "b" }
    }

    @Test
    fun `hasSpanSatisfying fails when no span matches`() {
        assertThrows<AssertionError> {
            SpanListAssert.assertThat(spans("a", "b"))
                .hasSpanSatisfying { it.name == "c" }
        }
    }

    @Test
    fun `allSatisfy passes when all match`() {
        SpanListAssert.assertThat(spans("GET /a", "GET /b"))
            .allSatisfy { it.name.startsWith("GET") }
    }

    @Test
    fun `allSatisfy fails with failing span names`() {
        val error = assertThrows<AssertionError> {
            SpanListAssert.assertThat(spans("GET /a", "POST /b"))
                .allSatisfy { it.name.startsWith("GET") }
        }
        assert(error.message!!.contains("POST /b"))
    }

    @Test
    fun `noneSatisfy passes when none match`() {
        SpanListAssert.assertThat(spans("GET /a", "GET /b"))
            .noneSatisfy { it.name.startsWith("DELETE") }
    }

    @Test
    fun `noneSatisfy fails with matching span names`() {
        val error = assertThrows<AssertionError> {
            SpanListAssert.assertThat(spans("GET /a", "DELETE /b"))
                .noneSatisfy { it.name.startsWith("DELETE") }
        }
        assert(error.message!!.contains("DELETE /b"))
    }

    @Test
    fun `extractingNames returns span names`() {
        val names = SpanListAssert.assertThat(spans("a", "b", "c")).extractingNames()
        assert(names == listOf("a", "b", "c"))
    }

    @Test
    fun `fluent chaining works`() {
        SpanListAssert.assertThat(spans("GET /api", "POST /api"))
            .isNotEmpty()
            .hasSize(2)
            .hasSpanWithName("GET /api")
            .hasSpanWithName("POST /api")
    }

    // -- helpers --

    private fun spans(vararg names: String): List<Span> =
        names.map { Span.newBuilder().setName(it).build() }
}