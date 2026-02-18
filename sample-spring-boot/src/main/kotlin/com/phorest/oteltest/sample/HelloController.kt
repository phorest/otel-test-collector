package com.phorest.oteltest.sample

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController
class HelloController(private val greetingService: GreetingService) {

    @GetMapping("/hello")
    fun hello(): String = "Hello, World!"

    @GetMapping("/greet/{name}")
    fun greet(@PathVariable name: String): String = greetingService.greet(name)

    @GetMapping("/fail")
    fun fail(): String = throw IllegalStateException("Something went wrong")
}