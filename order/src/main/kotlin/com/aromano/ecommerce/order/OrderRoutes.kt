package com.aromano.ecommerce.order

import com.aromano.ecommerce.order.domain.Order
import com.aromano.ecommerce.order.domain.OrderState
import com.aromano.ecommerce.order.domain.Product
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.context.request.async.DeferredResult
import java.util.concurrent.CompletableFuture
import kotlin.jvm.java

@RestController
@RequestMapping("/order")
class OrderRoutes(
    private val sagaRunner: SagaRunner,
    private val createOrderSagaFactory: CreateOrderSagaFactory,
    private val orderService: OrderService,
) {

    private val logger = LoggerFactory.getLogger(OrderRoutes::class.java)

    @GetMapping("/all")
    fun getAll(@RequestParam userId: Int): List<Order> {
        return orderService.getAllByUserId(userId)
    }

    @GetMapping("/{id}")
    fun getById(@PathVariable id: Int): Order {
        return orderService.findById(id)!!
    }

    @PostMapping
    fun createOrder(@RequestParam userId: Int): Order {
        val deferredResult = DeferredResult<Order>()
        val products = listOf(
            Product(1, "name 1", 83),
            Product(2, "name 2", 73),
        )

        logger.info("createOrder before thread: ${Thread.currentThread()}")
        val saga = createOrderSagaFactory.create()

        val result = sagaRunner.start(saga, CreateOrderSagaArgs(userId, products))
        val orderId = result.get().orderId
        logger.info("createOrder after thread: ${Thread.currentThread()}")
        return orderService.findById(orderId) ?: throw IllegalStateException()
    }

}