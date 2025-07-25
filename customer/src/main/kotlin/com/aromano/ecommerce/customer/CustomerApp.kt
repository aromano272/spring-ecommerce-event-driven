package com.aromano.ecommerce.customer

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class CustomerApp

fun main(args: Array<String>) {
    runApplication<CustomerApp>(*args)
}