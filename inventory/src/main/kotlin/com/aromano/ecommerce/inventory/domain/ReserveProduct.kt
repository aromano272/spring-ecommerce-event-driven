package com.aromano.ecommerce.inventory.domain

import com.aromano.ecommerce.common.Cents

data class ReserveProduct(
    val productId: Int,
    val userId: Int,
    val orderId: Int,
    val price: Cents,
    val quantity: Int,
)