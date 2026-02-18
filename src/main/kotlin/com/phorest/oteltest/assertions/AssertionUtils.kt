package com.phorest.oteltest.assertions

internal fun assert(condition: Boolean, message: () -> String) {
    if (!condition) throw AssertionError(message())
}

internal fun fail(message: String): Nothing = throw AssertionError(message)
