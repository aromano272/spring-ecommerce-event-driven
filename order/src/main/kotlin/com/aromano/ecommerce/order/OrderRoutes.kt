package com.aromano.ecommerce.order

import com.aromano.ecommerce.order.domain.Order
import com.aromano.ecommerce.order.domain.OrderState
import com.aromano.ecommerce.order.domain.Product
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/order")
class OrderRoutes(
    val orderDao: OrderDao,
) {

    @GetMapping("/all")
    fun getAll(@RequestParam userId: Int): List<Order> {
        return orderDao.getAllByUserId(userId)
    }

    @PostMapping
    fun createOrder(@RequestParam userId: Int): Order {
        val orderId = orderDao.insert(userId, OrderState.CREATED, listOf(
            Product(1, "name 1", 100),
            Product(2, "name 2", 200),
        ))

        val order = orderDao.findById(orderId!!)
        return order!!
    }

}