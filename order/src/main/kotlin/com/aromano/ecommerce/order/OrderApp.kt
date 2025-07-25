package com.aromano.ecommerce.order

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class OrderApp

fun main(args: Array<String>) {
    runApplication<OrderApp>(*args)
}