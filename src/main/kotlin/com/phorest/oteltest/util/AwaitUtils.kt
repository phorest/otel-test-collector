package com.phorest.oteltest.util

import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.until
import java.time.Duration

object AwaitUtils {

    fun awaitUntil(
        timeout: Duration = Duration.ofSeconds(10),
        pollInterval: Duration = Duration.ofMillis(100),
        condition: () -> Boolean
    ) {
        await.atMost(timeout).pollInterval(pollInterval).until { condition() }
    }

    fun <T> awaitUntilNotNull(
        timeout: Duration = Duration.ofSeconds(10),
        pollInterval: Duration = Duration.ofMillis(100),
        supplier: () -> T?
    ): T {
        var result: T? = null
        await.atMost(timeout).pollInterval(pollInterval).until {
            result = supplier()
            result != null
        }
        return result!!
    }
}