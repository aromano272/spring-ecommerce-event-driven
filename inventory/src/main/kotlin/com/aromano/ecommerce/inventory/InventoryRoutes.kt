package com.aromano.ecommerce.inventory

import com.aromano.ecommerce.common.Cents
import com.aromano.ecommerce.inventory.domain.Product
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/inventory")
class InventoryRoutes(val service: InventoryService) {

    @PostMapping
    fun create(): Product {
        return service.createProduct()
    }

    @GetMapping("/all")
    fun getAll(): List<Product> {
        return service.getAllProducts()
    }

    @GetMapping("/{id}")
    fun getAll(
        @PathVariable("id") id: Int,
    ): Product {
        return service.getProductById(id)!!
    }

    @PostMapping("/{id}/incrementInventory")
    fun incrementInventory(
        @PathVariable id: Int,
        @RequestParam amount: Cents,
    ) {
        service.incrementInventory(id, amount)
    }

    @PostMapping("/{id}/decrementInventory")
    fun debitBalance(
        @PathVariable id: Int,
        @RequestParam amount: Cents,
    ) {
        service.decrementInventory(id, amount)
    }

}