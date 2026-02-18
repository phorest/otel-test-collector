package com.phorest.oteltest.junit5

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class OtlpCollectorConfig(
    val port: Int = 4318,
    val resetBeforeEach: Boolean = true,
    val awaitTimeoutMs: Long = 10_000
)