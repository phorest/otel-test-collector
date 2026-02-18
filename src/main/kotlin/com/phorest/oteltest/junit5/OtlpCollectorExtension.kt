package com.phorest.oteltest.junit5

import com.phorest.oteltest.collector.OtlpTestCollector
import com.phorest.oteltest.model.SpanNode
import com.phorest.oteltest.model.TraceTree
import com.phorest.oteltest.util.AwaitUtils
import io.opentelemetry.proto.trace.v1.Span
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver
import java.time.Duration

class OtlpCollectorExtension private constructor(
    private var port: Int,
    private var resetBeforeEach: Boolean,
    private var awaitTimeout: Duration,
    private val shared: Boolean
) : BeforeAllCallback, AfterAllCallback, BeforeEachCallback, ParameterResolver {

    private var collector: OtlpTestCollector? = null

    private fun ensureStarted(context: ExtensionContext? = null): OtlpTestCollector {
        collector?.let { return it }

        val instance = if (shared) {
            createShared(context
                ?: error("Shared collector requires an ExtensionContext. Ensure the extension is registered via @RegisterExtension."))
        } else {
            OtlpTestCollector.builder().port(port).build().start()
        }

        collector = instance
        return instance
    }

    private fun createShared(context: ExtensionContext): OtlpTestCollector {
        val rootStore = context.root.getStore(NAMESPACE)
        val holder = rootStore.getOrComputeIfAbsent(
            SHARED_KEY,
            { _ ->
                SharedCollectorHolder(
                    OtlpTestCollector.builder().port(port).build().start()
                )
            },
            SharedCollectorHolder::class.java
        )
        return holder.collector
    }

    override fun beforeAll(context: ExtensionContext) {
        val config = context.requiredTestClass.getAnnotation(OtlpCollectorConfig::class.java)
        if (config != null) {
            port = config.port
            resetBeforeEach = config.resetBeforeEach
            awaitTimeout = Duration.ofMillis(config.awaitTimeoutMs)
        }
        ensureStarted(context)
    }

    override fun afterAll(context: ExtensionContext) {
        if (!shared) {
            collector?.close()
            collector = null
        }
    }

    override fun beforeEach(context: ExtensionContext) {
        val c = ensureStarted(context)
        if (resetBeforeEach) {
            c.reset()
        }
    }

    override fun supportsParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Boolean =
        parameterContext.parameter.type == OtlpTestCollector::class.java

    override fun resolveParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Any =
        ensureStarted(extensionContext)

    fun getCollector(): OtlpTestCollector = ensureStarted()

    fun getPort(): Int = ensureStarted().getPort()

    fun getSpans(): List<SpanNode> = ensureStarted().getSpans()

    fun awaitSpans(count: Int): List<SpanNode> =
        ensureStarted().awaitSpans(count, awaitTimeout)

    fun awaitSpan(predicate: (SpanNode) -> Boolean): SpanNode =
        ensureStarted().awaitSpan(awaitTimeout, predicate)

    fun traces(): List<TraceTree> = ensureStarted().traces()

    fun awaitTrace(predicate: (TraceTree) -> Boolean): TraceTree =
        AwaitUtils.awaitUntilNotNull(timeout = awaitTimeout) {
            ensureStarted().traces().firstOrNull(predicate)
        }

    fun reset() = ensureStarted().reset()

    companion object {
        private val NAMESPACE = ExtensionContext.Namespace.create(OtlpCollectorExtension::class.java)
        private const val SHARED_KEY = "sharedCollector"

        @JvmStatic
        fun create(): OtlpCollectorExtension = Builder().build()

        @JvmStatic
        fun builder(): Builder = Builder()
    }

    class Builder {
        private var port: Int = 4318
        private var resetBeforeEach: Boolean = true
        private var awaitTimeout: Duration = Duration.ofSeconds(10)
        private var shared: Boolean = false

        fun port(port: Int): Builder = apply { this.port = port }

        fun resetBeforeEach(reset: Boolean): Builder = apply { this.resetBeforeEach = reset }

        fun awaitTimeout(timeout: Duration): Builder = apply { this.awaitTimeout = timeout }

        fun shared(): Builder = apply { this.shared = true }

        fun build(): OtlpCollectorExtension =
            OtlpCollectorExtension(port, resetBeforeEach, awaitTimeout, shared)
    }

    private class SharedCollectorHolder(
        val collector: OtlpTestCollector
    ) : ExtensionContext.Store.CloseableResource {

        override fun close() {
            collector.close()
        }
    }
}