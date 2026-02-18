package com.phorest.oteltest.sample

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.StatusCode
import org.springframework.stereotype.Service

@Service
class GreetingService {

    private val tracer = GlobalOpenTelemetry.getTracer("greeting-service")

    fun greet(name: String): String {
        val span = tracer.spanBuilder("GreetingService.greet")
            .setAttribute("greeting.name", name)
            .startSpan()

        return span.makeCurrent().use {
            try {
                val greeting = buildGreeting(name)
                span.setAttribute("greeting.length", greeting.length.toLong())
                span.setStatus(StatusCode.OK)
                greeting
            } finally {
                span.end()
            }
        }
    }

    private fun buildGreeting(name: String): String {
        val span = tracer.spanBuilder("GreetingService.buildGreeting")
            .startSpan()

        return span.makeCurrent().use {
            try {
                Thread.sleep(5) // simulate some work
                "Hello, $name!"
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw RuntimeException(e)
            } finally {
                span.end()
            }
        }
    }
}