package com.phorest.oteltest.junit5

import com.phorest.oteltest.collector.OtlpTestCollector
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest
import io.opentelemetry.proto.trace.v1.ResourceSpans
import io.opentelemetry.proto.trace.v1.ScopeSpans
import io.opentelemetry.proto.trace.v1.Span
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.api.extension.RegisterExtension
import java.net.HttpURLConnection
import java.net.URI

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
            sendSpan(collector.getPort(), "test-span")

            val spans = collector.awaitSpans(1)
            assertEquals(1, spans.size)
            assertEquals("test-span", spans.first().name)
        }

        @Test
        fun `getSpans returns captured spans`() {
            sendSpan(collector.getPort(), "my-span")
            collector.awaitSpans(1)

            assertEquals(1, collector.getSpans().size)
        }

        @Test
        fun `awaitSpan waits for matching span`() {
            sendSpan(collector.getPort(), "find-me")

            val span = collector.awaitSpan { it.name == "find-me" }
            assertEquals("find-me", span.name)
        }
    }

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
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
            sendSpan(collector.getPort(), "first-test-span")
            collector.awaitSpans(1)
            assertEquals(1, collector.getSpans().size)
        }

        @Test
        @Order(2)
        fun `second test starts with empty spans due to reset`() {
            // resetBeforeEach=true means spans from previous test are cleared
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
            sendSpan(collector.getPort(), "first-span")
            collector.awaitSpans(1)

            sendSpan(collector.getPort(), "second-span")
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

            sendSpan(collector.getPort(), "injected-test")
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
            sendSpan(collector.getPort(), "before-reset")
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
            sendSpan(collector.getPort(), "shared-span")

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

    // -- helpers --

    private fun sendSpan(port: Int, name: String) {
        val request = ExportTraceServiceRequest.newBuilder()
            .addResourceSpans(
                ResourceSpans.newBuilder()
                    .addScopeSpans(
                        ScopeSpans.newBuilder()
                            .addSpans(Span.newBuilder().setName(name))
                    )
            )
            .build()

        val url = URI("http://localhost:$port/v1/traces").toURL()
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/x-protobuf")
            connection.outputStream.use { it.write(request.toByteArray()) }
            connection.responseCode
        } finally {
            connection.disconnect()
        }
    }
}