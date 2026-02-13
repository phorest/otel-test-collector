package com.phorest.oteltest.assertions

import com.phorest.oteltest.TraceBuilder
import com.phorest.oteltest.dsl.assertThat
import com.phorest.oteltest.model.TraceTree
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TraceTreeAssertTest {

    private val traceBuilder = TraceBuilder()

    private fun buildTree(vararg spans: Pair<String, String?>): TraceTree =
        TraceTree.buildFrom(traceBuilder.buildTrace(*spans))

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
    fun `leaf span without block`() {
        buildTree("root" to null, "child" to "root").assertThat {
            rootSpan("root") {
                child("child")
            }
        }
    }

    @Test
    fun `full fluent chain with awaitTrace pattern`() {
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
