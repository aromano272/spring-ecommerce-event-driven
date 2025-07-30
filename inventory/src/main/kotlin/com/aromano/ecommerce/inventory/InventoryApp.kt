package com.aromano.ecommerce.inventory

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class InventoryApp

fun main(args: Array<String>) {
    runApplication<InventoryApp>(*args)
}