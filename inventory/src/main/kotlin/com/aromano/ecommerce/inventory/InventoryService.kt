package com.aromano.ecommerce.inventory

import com.aromano.ecommerce.common.Cents
import com.aromano.ecommerce.inventory.domain.Product
import org.springframework.stereotype.Service

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
        dao.decrementInventoryIfPossibleTx(id, amount)
    }

    fun decrementInventoryForOrder(event: DecrementInventoryCommand) {
        try {
            decrementInventory(event.userId, event.amount)
            eventProducer.send(event.orderId.toString(), InventoryDecrementSuccess(event.sagaId, event.orderId))
        } catch (ex: Exception) {
            eventProducer.send(event.orderId.toString(), InventoryDecrementFailed(event.sagaId, event.orderId, ex.toString()))
        }
    }

}