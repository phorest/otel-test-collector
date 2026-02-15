package com.phorest.oteltest.dsl

import com.phorest.oteltest.TraceBuilder
import com.phorest.oteltest.model.TraceTree
import com.phorest.oteltest.spanDef
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
        fun `containsSpan matches with span query builder`() {
            val trace = buildTree("POST /orders" to null)
            assertTrue(query { containsSpan { withNameContaining("POST") } }.matches(trace))
        }

        @Test
        fun `containsSpan does not match when no span satisfies query`() {
            val trace = buildTree("GET /api" to null)
            assertFalse(query { containsSpan { withNameContaining("POST") } }.matches(trace))
        }

        @Test
        fun `containsSpan with multiple predicates matches span satisfying all`() {
            val spans = traceBuilder.buildTrace(listOf(
                spanDef("DB query", null, "db.table" to "users")
            ))
            val trace = TraceTree.buildFrom(spans)
            assertTrue(query {
                containsSpan {
                    withName("DB query")
                    withAttribute("db.table", "users")
                }
            }.matches(trace))
        }

        @Test
        fun `containsSpan with multiple predicates does not match when one fails`() {
            val spans = traceBuilder.buildTrace(listOf(
                spanDef("DB query", null, "db.table" to "users")
            ))
            val trace = TraceTree.buildFrom(spans)
            assertFalse(query {
                containsSpan {
                    withName("DB query")
                    withAttribute("db.table", "orders")
                }
            }.matches(trace))
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
        fun `span passes for existing span`() {
            buildTree("root" to null, "child" to "root").assertThat {
                rootSpan("root") {
                    span("child")
                }
            }
        }

        @Test
        fun `span fails for missing span`() {
            val error = assertThrows<AssertionError> {
                buildTree("root" to null, "child" to "root").assertThat {
                    rootSpan("root") {
                        span("missing")
                    }
                }
            }
            assert(error.message!!.contains("missing"))
            assert(error.message!!.contains("root"))
            assert(error.message!!.contains("child"))
        }

        @Test
        fun `nested spans pass for correct structure`() {
            buildTree("root" to null, "child" to "root", "grandchild" to "child").assertThat {
                rootSpan("root") {
                    span("child") {
                        span("grandchild")
                    }
                }
            }
        }

        @Test
        fun `span fails when not a direct descendant`() {
            assertThrows<AssertionError> {
                buildTree("root" to null, "child" to "root", "grandchild" to "child").assertThat {
                    rootSpan("root") {
                        span("grandchild")
                    }
                }
            }
        }

        @Test
        fun `multiple spans at same level`() {
            buildTree("root" to null, "child1" to "root", "child2" to "root").assertThat {
                rootSpan("root") {
                    span("child1")
                    span("child2")
                }
            }
        }

        @Test
        fun `hasSpans passes for correct count`() {
            buildTree("root" to null, "DB query" to "root", "DB query" to "root").assertThat {
                rootSpan("root") {
                    hasSpans("DB query", 2)
                }
            }
        }

        @Test
        fun `hasSpans fails for wrong count`() {
            val error = assertThrows<AssertionError> {
                buildTree("root" to null, "DB query" to "root", "DB query" to "root").assertThat {
                    rootSpan("root") {
                        hasSpans("DB query", 3)
                    }
                }
            }
            assert(error.message!!.contains("3"))
            assert(error.message!!.contains("2"))
        }

        @Test
        fun `span with index accesses correct span and allows assertions`() {
            val spans = traceBuilder.buildTrace(listOf(
                spanDef("root", null),
                spanDef("DB query", "root", "db.table" to "users"),
                spanDef("DB query", "root", "db.table" to "orders")
            ))

            TraceTree.buildFrom(spans).assertThat {
                rootSpan("root") {
                    hasSpans("DB query", 2)
                    span("DB query")[0] {
                        hasAttribute("db.table", "users")
                    }
                    span("DB query")[1] {
                        hasAttribute("db.table", "orders")
                    }
                }
            }
        }

        @Test
        fun `span with index fails when out of bounds`() {
            val error = assertThrows<AssertionError> {
                buildTree("root" to null, "DB query" to "root").assertThat {
                    rootSpan("root") {
                        span("DB query")[1]
                    }
                }
            }
            assert(error.message!!.contains("index [1]"))
            assert(error.message!!.contains("1] spans"))
        }

        @Test
        fun `full trace structure`() {
            buildTree(
                "GET /greet/{name}" to null,
                "GreetingService.greet" to "GET /greet/{name}",
                "GreetingService.buildGreeting" to "GreetingService.greet"
            ).assertThat {
                rootSpan("GET /greet/{name}") {
                    span("GreetingService.greet") {
                        span("GreetingService.buildGreeting")
                    }
                }
            }
        }

        @Test
        fun `anySpan finds span nested deep in tree`() {
            buildTree(
                "root" to null,
                "child" to "root",
                "grandchild" to "child"
            ).assertThat {
                anySpan("grandchild")
            }
        }

        @Test
        fun `anySpan fails when no span matches name`() {
            val error = assertThrows<AssertionError> {
                buildTree("root" to null, "child" to "root").assertThat {
                    anySpan("missing")
                }
            }
            assert(error.message!!.contains("missing"))
        }

        @Test
        fun `anySpan allows assertions on found span`() {
            val spans = traceBuilder.buildTrace(listOf(
                spanDef("root", null),
                spanDef("child", "root"),
                spanDef("DB query", "child", "db.table" to "users")
            ))

            TraceTree.buildFrom(spans).assertThat {
                anySpan("DB query") {
                    hasAttribute("db.table", "users")
                }
            }
        }

        @Test
        fun `anySpan with predicate finds matching span`() {
            buildTree(
                "root" to null,
                "child" to "root",
                "DB query" to "child"
            ).assertThat {
                anySpan({ it.name.startsWith("DB") })
            }
        }

        @Test
        fun `anySpan with predicate fails when no span matches`() {
            val error = assertThrows<AssertionError> {
                buildTree("root" to null).assertThat {
                    anySpan({ it.name == "nope" })
                }
            }
            assert(error.message!!.contains("No span matching predicate"))
        }
    }
}