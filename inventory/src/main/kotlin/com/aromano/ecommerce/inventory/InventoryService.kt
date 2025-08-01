package com.aromano.ecommerce.inventory

import com.aromano.ecommerce.common.Cents
import com.aromano.ecommerce.inventory.domain.Product
import com.aromano.ecommerce.inventory.domain.ReserveProduct
import io.micrometer.core.instrument.config.validate.Validated.valid
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event

@Service
class InventoryService(
    val dao: InventoryDao,
    val eventProducer: EventProducer,
) {

    fun createProduct(): Product {
        val id = dao.insert()!!
        return dao.findById(id)!!
    }

    fun getAllProducts(): List<Product> = dao.getAll()

    fun getProductById(id: Int): Product? = dao.findById(id)

    fun incrementInventory(id: Int, amount: Cents) {
        dao.incrementInventory(id, amount)
    }

    fun decrementInventory(id: Int, amount: Cents) {
        dao.decrementInventory(id, amount)
    }

    fun reserveInventoryForOrder(event: ReserveInventoryCommand) {
        val productsById = event.products
            .groupBy { it.id }
            .mapValues { (_, products) ->
                if (products.toSet().size != 1) throw IllegalArgumentException("Products with the same id dont match price")
                ReserveProduct(
                    productId = products.first().id,
                    userId = event.userId,
                    orderId = event.orderId,
                    price = products.first().price,
                    quantity = products.size,
                )
            }
        val products = productsById.values.toList()

        try {
            dao.validateAndReserveInventoryForOrderTx(
                userId = event.userId,
                orderId = event.orderId,
                reserveProducts = products,
                validate = { product ->
                    val reserve = productsById[product.id]!!
                    val available = product.inventory - product.reservedInventory
                    if (reserve.price != product.price) throw IllegalArgumentException("Invalid product, reserve: $reserve, product: $product")
                    if (available < reserve.quantity) throw IllegalArgumentException("Not enough inventory available to fulfill order, reserve: $reserve, product: $product")
                },
            )
            val totalCost = products.sumOf { it.price * it.quantity }
            eventProducer.send(event.orderId.toString(), ReserveInventorySuccess(event.sagaId, event.orderId, totalCost))
        } catch (ex: Exception) {
            eventProducer.send(event.orderId.toString(), ReserveInventoryFailed(event.sagaId, event.orderId, ex.toString()))
        }
    }

    fun submitReservedInventoryForOrder(event: SubmitReservedInventoryCommand) {
        try {
            dao.submitReservedInventoryForOrderTx(event.userId, event.orderId)
            eventProducer.send(event.orderId.toString(), SubmitReservedInventorySuccess(event.sagaId, event.orderId))
        } catch (ex: Exception) {
            eventProducer.send(event.orderId.toString(), SubmitReservedInventoryFailed(event.sagaId, event.orderId, ex.toString()))
        }
    }

    fun releaseReservedInventory(event: RollbackReserveInventoryCommand) {
        try {
            dao.releaseReservedInventoryForOrderTx(event.userId, event.orderId)
            eventProducer.send(event.orderId.toString(), ReleasedReservedInventorySuccess(event.sagaId, event.orderId))
        } catch (ex: Exception) {
            eventProducer.send(event.orderId.toString(), ReleasedReservedInventoryFailed(event.sagaId, event.orderId, ex.toString()))
        }
    }

}