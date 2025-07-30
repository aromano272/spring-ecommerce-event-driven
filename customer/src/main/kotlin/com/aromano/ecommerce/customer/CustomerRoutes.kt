package com.aromano.ecommerce.customer

import com.aromano.ecommerce.common.Cents
import com.aromano.ecommerce.customer.domain.Customer
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/customer")
class CustomerRoutes(val service: CustomerService) {

    @PostMapping
    fun create(): Customer {
        return service.createCustomer()
    }

    @GetMapping("/all")
    fun getAll(): List<Customer> {
        return service.getAllCustomers()
    }

    @GetMapping("/{id}")
    fun getAll(
        @PathVariable("id") id: Int,
    ): Customer {
        return service.getCustomerById(id)!!
    }

    @PostMapping("/{userId}/credit")
    fun creditBalance(
        @PathVariable userId: Int,
        @RequestParam amount: Cents,
    ) {
        service.incrementBalance(userId, amount)
    }

    @PostMapping("/{userId}/debit")
    fun debitBalance(
        @PathVariable userId: Int,
        @RequestParam amount: Cents,
    ) {
        service.decrementBalance(userId, amount)
    }

}