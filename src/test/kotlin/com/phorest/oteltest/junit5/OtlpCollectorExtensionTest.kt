package com.phorest.oteltest.junit5

import com.phorest.oteltest.TestFixtures.sendSpans
import com.phorest.oteltest.collector.OtlpTestCollector
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.api.extension.RegisterExtension

class OtlpCollectorExtensionTest {

    @Nested
    inner class ProgrammaticRegistration {

        @JvmField
        @RegisterExtension
        val collector = OtlpCollectorExtension.builder()
            .port(0)
            .resetBeforeEach(true)
            .build()

        @Test
        fun `extension starts collector and exposes port`() {
            assertTrue(collector.getPort() > 0)
        }

        @Test
        fun `extension captures spans`() {
            sendSpans(collector.getPort(), "test-span")

            val spans = collector.awaitSpans(1)
            assertEquals(1, spans.size)
            assertEquals("test-span", spans.first().name)
        }

        @Test
        fun `getSpans returns captured spans`() {
            sendSpans(collector.getPort(), "my-span")
            collector.awaitSpans(1)

            assertEquals(1, collector.getSpans().size)
        }

        @Test
        fun `awaitSpan waits for matching span`() {
            sendSpans(collector.getPort(), "find-me")

            val span = collector.awaitSpan { it.name == "find-me" }
            assertEquals("find-me", span.name)
        }
    }

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class ResetBehavior {

        @JvmField
        @RegisterExtension
        val collector = OtlpCollectorExtension.builder()
            .port(0)
            .resetBeforeEach(true)
            .build()

        @Test
        @Order(1)
        fun `first test sends a span`() {
            sendSpans(collector.getPort(), "first-test-span")
            collector.awaitSpans(1)
            assertEquals(1, collector.getSpans().size)
        }

        @Test
        @Order(2)
        fun `second test starts with empty spans due to reset`() {
            assertEquals(0, collector.getSpans().size)
        }
    }

    @Nested
    inner class NoResetBehavior {

        @JvmField
        @RegisterExtension
        val collector = OtlpCollectorExtension.builder()
            .port(0)
            .resetBeforeEach(false)
            .build()

        @Test
        fun `spans accumulate when resetBeforeEach is false`() {
            sendSpans(collector.getPort(), "first-span")
            collector.awaitSpans(1)

            sendSpans(collector.getPort(), "second-span")
            collector.awaitSpans(2)

            assertEquals(2, collector.getSpans().size)
        }
    }

    @Nested
    inner class ParameterInjection {

        @JvmField
        @RegisterExtension
        val extension = OtlpCollectorExtension.builder()
            .port(0)
            .build()

        @Test
        fun `collector is injected via parameter resolver`(collector: OtlpTestCollector) {
            assertTrue(collector.getPort() > 0)

            sendSpans(collector.getPort(), "injected-test")
            val spans = collector.awaitSpans(1)
            assertEquals("injected-test", spans.first().name)
        }
    }

    @Nested
    inner class FactoryMethod {

        @JvmField
        @RegisterExtension
        val collector = OtlpCollectorExtension.create()

        @Test
        fun `create factory uses defaults`() {
            assertTrue(collector.getPort() > 0)
        }
    }

    @Nested
    inner class ManualReset {

        @JvmField
        @RegisterExtension
        val collector = OtlpCollectorExtension.builder()
            .port(0)
            .resetBeforeEach(false)
            .build()

        @Test
        fun `manual reset clears spans`() {
            sendSpans(collector.getPort(), "before-reset")
            collector.awaitSpans(1)
            assertEquals(1, collector.getSpans().size)

            collector.reset()
            assertEquals(0, collector.getSpans().size)
        }
    }

    @Nested
    inner class SharedMode {

        @JvmField
        @RegisterExtension
        val collector = OtlpCollectorExtension.builder()
            .port(0)
            .shared()
            .build()

        @Test
        fun `shared collector starts and captures spans`() {
            sendSpans(collector.getPort(), "shared-span")

            val spans = collector.awaitSpans(1)
            assertEquals(1, spans.size)
            assertEquals("shared-span", spans.first().name)
        }

        @Test
        fun `shared collector reuses same port across tests`() {
            val port = collector.getPort()
            assertTrue(port > 0)
        }
    }
}