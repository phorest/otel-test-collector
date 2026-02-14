package com.phorest.oteltest.dsl

import com.phorest.oteltest.TraceBuilder
import com.phorest.oteltest.model.TraceTree
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TraceQueryDslTest {

    private val traceBuilder = TraceBuilder()

    private fun buildTree(vararg spans: Pair<String, String?>): TraceTree =
        TraceTree.buildFrom(traceBuilder.buildTrace(*spans))

    private fun query(block: TraceQueryBuilder.() -> Unit): TraceQueryBuilder =
        TraceQueryBuilder().apply(block)

    @Nested
    inner class TraceQueryBuilderTest {

        @Test
        fun `containsSpanNamed matches trace with named span`() {
            val trace = buildTree("root" to null, "DB query" to "root")
            assertTrue(query { containsSpanNamed("DB query") }.matches(trace))
        }

        @Test
        fun `containsSpanNamed does not match when span is absent`() {
            val trace = buildTree("root" to null)
            assertFalse(query { containsSpanNamed("missing") }.matches(trace))
        }

        @Test
        fun `hasMinSpanCount matches trace with enough spans`() {
            val trace = buildTree("root" to null, "child" to "root", "grandchild" to "child")
            assertTrue(query { hasMinSpanCount(2) }.matches(trace))
        }

        @Test
        fun `hasMinSpanCount does not match trace with fewer spans`() {
            val trace = buildTree("root" to null)
            assertFalse(query { hasMinSpanCount(2) }.matches(trace))
        }

        @Test
        fun `containsSpanMatching matches with custom predicate`() {
            val trace = buildTree("POST /orders" to null)
            assertTrue(query { containsSpanMatching { it.name.startsWith("POST") } }.matches(trace))
        }

        @Test
        fun `containsSpanMatching does not match when predicate fails`() {
            val trace = buildTree("GET /api" to null)
            assertFalse(query { containsSpanMatching { it.name.startsWith("POST") } }.matches(trace))
        }

        @Test
        fun `multiple predicates are combined with AND`() {
            val trace = buildTree("root" to null, "DB query" to "root")
            assertTrue(query { containsSpanNamed("DB query"); hasMinSpanCount(2) }.matches(trace))
            assertFalse(query { containsSpanNamed("DB query"); hasMinSpanCount(5) }.matches(trace))
        }
    }

    @Nested
    inner class AssertDslTest {

        @Test
        fun `rootSpan passes for correct name`() {
            buildTree("root" to null, "child" to "root").assertThat {
                rootSpan("root")
            }
        }

        @Test
        fun `rootSpan fails for wrong name`() {
            val error = assertThrows<AssertionError> {
                buildTree("actual-root" to null).assertThat {
                    rootSpan("expected-root")
                }
            }
            assert(error.message!!.contains("expected-root"))
            assert(error.message!!.contains("actual-root"))
        }

        @Test
        fun `child passes for existing child`() {
            buildTree("root" to null, "child" to "root").assertThat {
                rootSpan("root") {
                    child("child")
                }
            }
        }

        @Test
        fun `child fails for missing child`() {
            val error = assertThrows<AssertionError> {
                buildTree("root" to null, "child" to "root").assertThat {
                    rootSpan("root") {
                        child("missing")
                    }
                }
            }
            assert(error.message!!.contains("missing"))
            assert(error.message!!.contains("root"))
            assert(error.message!!.contains("child"))
        }

        @Test
        fun `nested children pass for correct structure`() {
            buildTree("root" to null, "child" to "root", "grandchild" to "child").assertThat {
                rootSpan("root") {
                    child("child") {
                        child("grandchild")
                    }
                }
            }
        }

        @Test
        fun `nested child fails when not a direct child`() {
            assertThrows<AssertionError> {
                buildTree("root" to null, "child" to "root", "grandchild" to "child").assertThat {
                    rootSpan("root") {
                        child("grandchild")
                    }
                }
            }
        }

        @Test
        fun `multiple children at same level`() {
            buildTree("root" to null, "child1" to "root", "child2" to "root").assertThat {
                rootSpan("root") {
                    child("child1")
                    child("child2")
                }
            }
        }

        @Test
        fun `full trace structure`() {
            buildTree(
                "GET /greet/{name}" to null,
                "GreetingService.greet" to "GET /greet/{name}",
                "GreetingService.buildGreeting" to "GreetingService.greet"
            ).assertThat {
                rootSpan("GET /greet/{name}") {
                    child("GreetingService.greet") {
                        child("GreetingService.buildGreeting")
                    }
                }
            }
        }
    }
}