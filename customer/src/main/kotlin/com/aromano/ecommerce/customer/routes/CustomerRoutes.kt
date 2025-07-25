package com.aromano.ecommerce.customer.routes

import com.aromano.ecommerce.customer.domain.Cents
import com.aromano.ecommerce.customer.domain.Customer
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/customer")
class CustomerRoutes {

    @PostMapping
    fun create(@RequestBody request: CreateCustomerRequest): Customer {
        val customer = Customer(
            request.userId,
            balance = 0,
        )
        return customer
    }

    @PostMapping("/{userId}/credit")
    fun creditBalance(
        @PathVariable userId: Int,
        @RequestParam amount: Cents,
    ) {
    }

    @PostMapping("/{userId}/debit")
    fun debitBalance(
        @PathVariable userId: Int,
        @RequestParam amount: Cents,
    ) {
    }

}

data class CreateCustomerRequest(
    val userId: Int,
)