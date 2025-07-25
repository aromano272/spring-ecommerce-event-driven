package com.aromano.ecommerce.order

import com.aromano.ecommerce.order.domain.OrderState
import com.aromano.ecommerce.order.domain.Product
import org.springframework.stereotype.Service

@Service
class OrderService(
    private val dao: OrderDao,
) {

    fun createOrder(userId: Int, products: List<Product>): Int {
        val id = dao.insert(
            userId = userId,
            state = OrderState.CREATED,
            products = products,
        )!!
        return id
    }

    fun completeOrderCreation(id: Int) {
        dao.updateState(
            id = id,
            state = OrderState.IN_PROGRESS,
        )
    }

    fun rejectOrder(id: Int) {
        dao.updateState(
            id = id,
            state = OrderState.REJECTED,
        )
    }

}