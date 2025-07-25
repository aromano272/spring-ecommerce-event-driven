package com.aromano.ecommerce.order.domain

import com.aromano.ecommerce.common.Cents
import org.jdbi.v3.json.Json

data class Order(
    val id: Int?,
    val userId: Int,
    val state: OrderState,
    @Json
    val products: List<Product>,
)

enum class OrderState {
    CREATED,
    IN_PROGRESS,
    COMPLETED,
    REJECTED,
}

data class Product(
    val id: Int,
    val name: String,
    val price: Cents,
)