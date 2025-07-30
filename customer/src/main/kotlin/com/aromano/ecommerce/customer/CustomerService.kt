package com.aromano.ecommerce.customer

import com.aromano.ecommerce.common.Cents
import com.aromano.ecommerce.customer.domain.Customer
import org.springframework.stereotype.Service

@Service
class CustomerService(
    val dao: CustomerDao,
    val eventProducer: EventProducer,
) {

    fun createCustomer(): Customer {
        val id = dao.insert()!!
        return dao.findById(id)!!
    }

    fun getAllCustomers(): List<Customer> = dao.getAll()

    fun getCustomerById(id: Int): Customer? = dao.findById(id)

    fun incrementBalance(id: Int, amount: Cents) {
        dao.incrementBalance(id, amount)
    }

    fun decrementBalance(id: Int, amount: Cents) {
        dao.decrementBalanceIfPossibleTx(id, amount)
    }

    fun decrementBalanceForOrder(event: DecrementBalanceCommand) {
        try {
            decrementBalance(event.userId, event.amount)
            eventProducer.send(event.orderId.toString(), BalanceDecrementSuccess(event.sagaId, event.orderId))
        } catch (ex: Exception) {
            eventProducer.send(event.orderId.toString(), BalanceDecrementFailed(event.sagaId, event.orderId, ex.toString()))
        }
    }

}