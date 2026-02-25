package com.matcha

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class Matcha

fun main(args: Array<String>) {
    runApplication<Matcha>(*args)
}
