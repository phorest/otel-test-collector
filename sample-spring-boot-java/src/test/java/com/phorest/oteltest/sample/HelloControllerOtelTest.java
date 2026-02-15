package com.phorest.oteltest.sample;

import com.phorest.oteltest.junit5.OtlpCollectorExtension;
import io.opentelemetry.proto.trace.v1.Span;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HelloControllerOtelTest {

    @RegisterExtension
    static OtlpCollectorExtension collector = OtlpCollectorExtension.builder()
            .port(4318)
            .resetBeforeEach(true)
            .build();

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void capturesHttpServerSpanForGetHello() {
        var response = restTemplate.getForEntity("/hello", String.class);
        assertEquals(200, response.getStatusCode().value());
        assertEquals("Hello, World!", response.getBody());

        collector.awaitSpan(span ->
                span.getKind() == Span.SpanKind.SPAN_KIND_SERVER && span.getName().contains("hello")
        ).assertThat()
                .hasKind(Span.SpanKind.SPAN_KIND_SERVER);
    }

    @Test
    void capturesErrorSpanWithExceptionEvent() {
        restTemplate.getForEntity("/fail", String.class);

        collector.awaitSpan(span ->
                span.getKind() == Span.SpanKind.SPAN_KIND_SERVER && span.getName().contains("fail")
        ).assertThat()
                .hasKind(Span.SpanKind.SPAN_KIND_SERVER)
                .hasStatusError()
                .hasEvent("exception", event -> {
                    event.hasAttribute("exception.type", "java.lang.IllegalStateException");
                    event.hasAttribute("exception.message", "Something went wrong");
                });
    }

    @Test
    void verifiesTraceStructureForGreetEndpoint() {
        restTemplate.getForEntity("/greet/Darek", String.class);

        var trace = collector.awaitTrace(t ->
                t.spanNames().contains("GreetingService.greet")
        );
        trace.print();
        trace.assertThat()
                .hasSpanCount(3)
                .hasRootSpan("GET /greet/{name}")
                .spanWithName("GreetingService.buildGreeting")
                .hasParent("GreetingService.greet");
    }

    @Test
    void verifiesCustomAttributesOnGreetingSpan() {
        restTemplate.getForEntity("/greet/Darek", String.class);

        collector.awaitSpan(span ->
                span.getName().equals("GreetingService.greet")
        ).assertThat()
                .hasAttribute("greeting.name", "Darek")
                .hasStatusOk();
    }

}
