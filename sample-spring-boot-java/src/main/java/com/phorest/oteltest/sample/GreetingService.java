package com.phorest.oteltest.sample;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.springframework.stereotype.Service;

@Service
public class GreetingService {

    private final Tracer tracer = GlobalOpenTelemetry.getTracer("greeting-service");

    public String greet(String name) {
        Span span = tracer.spanBuilder("GreetingService.greet")
                .setAttribute("greeting.name", name)
                .startSpan();

        try (Scope scope = span.makeCurrent()) {
            String greeting = buildGreeting(name);
            span.setAttribute("greeting.length", (long) greeting.length());
            span.setStatus(StatusCode.OK);
            return greeting;
        } finally {
            span.end();
        }
    }

    private String buildGreeting(String name) {
        Span span = tracer.spanBuilder("GreetingService.buildGreeting")
                .startSpan();

        try (Scope scope = span.makeCurrent()) {
            Thread.sleep(5); // simulate some work
            return "Hello, " + name + "!";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } finally {
            span.end();
        }
    }
}
