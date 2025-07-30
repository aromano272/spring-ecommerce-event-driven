package com.aromano.ecommerce.customer.domain

import com.aromano.ecommerce.common.Cents

data class Customer(
    val id: Int,
    val balance: Cents,
)