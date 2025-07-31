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

    fun reserveBalanceForOrder(event: ReserveBalanceCommand) {
        try {
            dao.reserveBalanceIfPossibleTx(event.userId, event.orderId, event.amount)
            eventProducer.send(event.orderId.toString(), ReserveBalanceSuccess(event.sagaId, event.orderId))
        } catch (ex: Exception) {
            eventProducer.send(event.orderId.toString(), ReserveBalanceFailed(event.sagaId, event.orderId, ex.toString()))
        }
    }

    fun submitReservedBalanceForOrder(event: SubmitReservedBalanceCommand) {
        try {
            dao.submitReservedBalanceForOrderTx(event.userId, event.orderId)
            eventProducer.send(event.orderId.toString(), SubmitReservedBalanceSuccess(event.sagaId, event.orderId))
        } catch (ex: Exception) {
            eventProducer.send(event.orderId.toString(), SubmitReservedBalanceFailed(event.sagaId, event.orderId, ex.toString()))
        }
    }

    fun releaseReservedBalanceForOrder(event: RollbackReserveBalanceCommand) {
        try {
            dao.releaseReservedBalanceForOrderTx(event.userId, event.orderId)
            eventProducer.send(event.orderId.toString(), ReleasedReservedBalanceSuccess(event.sagaId, event.orderId))
        } catch (ex: Exception) {
            eventProducer.send(event.orderId.toString(), ReleasedReservedBalanceFailed(event.sagaId, event.orderId, ex.toString()))
        }
    }

}