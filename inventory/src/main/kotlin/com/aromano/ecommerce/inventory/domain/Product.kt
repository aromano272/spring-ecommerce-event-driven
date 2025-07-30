package com.aromano.ecommerce.inventory.domain

import com.aromano.ecommerce.common.Cents

data class Product(
    val id: Int,
    val name: String,
    val price: Cents,
    val inventory: Int,
)
