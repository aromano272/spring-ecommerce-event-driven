package com.aromano.ecommerce.customer

import com.aromano.ecommerce.common.Cents
import com.aromano.ecommerce.customer.domain.Customer
import org.jdbi.v3.sqlobject.customizer.Bind
import org.jdbi.v3.sqlobject.kotlin.RegisterKotlinMapper
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys
import org.jdbi.v3.sqlobject.statement.SqlQuery
import org.jdbi.v3.sqlobject.statement.SqlUpdate
import org.jdbi.v3.sqlobject.transaction.Transaction
import org.springframework.stereotype.Repository

@Repository
@RegisterKotlinMapper(Customer::class)
interface CustomerDao {

    @SqlUpdate("INSERT INTO customers DEFAULT VALUES")
    @GetGeneratedKeys
    fun insert(): Int?

    @SqlUpdate("UPDATE customers SET balance = balance + :amount WHERE id = :id")
    fun incrementBalance(
        @Bind("id") id: Int,
        @Bind("amount") amount: Cents,
    )

    @SqlUpdate("UPDATE customers SET balance = balance - :amount WHERE id = :id")
    fun decrementBalance(
        @Bind("id") id: Int,
        @Bind("amount") amount: Cents,
    )

    @SqlUpdate("UPDATE customers SET reservedBalance = reservedBalance + :amount WHERE id = :id")
    fun incrementReservedBalance(
        @Bind("id") id: Int,
        @Bind("amount") amount: Cents,
    )

    @SqlUpdate("UPDATE customers SET reservedBalance = reservedBalance - :amount WHERE id = :id")
    fun decrementReservedBalance(
        @Bind("id") id: Int,
        @Bind("amount") amount: Cents,
    )

    @SqlUpdate("""
        INSERT INTO reserved_balances (userId, orderId, balance)
        VALUES (:userId, :orderId, :balance)
    """)
    fun insertReservedBalanceForOrder(
        @Bind("userId") userId: Int,
        @Bind("orderId") orderId: Int,
        @Bind("balance") balance: Int,
    )

    @SqlUpdate("DELETE FROM reserved_balances WHERE userId = :userId AND orderId = :orderId")
    fun deleteReservedBalanceForOrder(
        @Bind("userId") userId: Int,
        @Bind("orderId") orderId: Int,
    )

    @Transaction
    fun reserveBalanceIfPossibleTx(
        id: Int,
        orderId: Int,
        amount: Cents,
    ) {
        val customer = findById(id) ?: throw IllegalArgumentException("customer with id $id not found")
        if (customer.balance < amount) throw IllegalArgumentException("customer has not enough balance to perform debit")
        insertReservedBalanceForOrder(id, orderId, amount)
        incrementReservedBalance(id, amount)
    }

    @Transaction
    fun submitReservedBalanceForOrderTx(id: Int, orderId: Int) {
        val amount = getReservedBalanceAmountByUserIdAndOrderId(id, orderId)
            ?: throw IllegalArgumentException("Reserved balance for userId: $id, orderId: $orderId not found")
        deleteReservedBalanceForOrder(id, orderId)
        decrementReservedBalance(id, amount)
        decrementBalance(id, amount)
    }

    @Transaction
    fun releaseReservedBalanceForOrderTx(id: Int, orderId: Int) {
        val amount = getReservedBalanceAmountByUserIdAndOrderId(id, orderId)
            ?: throw IllegalArgumentException("Reserved balance for userId: $id, orderId: $orderId not found")
        deleteReservedBalanceForOrder(id, orderId)
        decrementReservedBalance(id, amount)
    }

    @Transaction
    fun decrementBalanceIfPossibleTx(id: Int, amount: Cents) {
        val customer = findById(id) ?: throw IllegalArgumentException("customer with id $id not found")
        if (customer.balance < amount) throw IllegalArgumentException("customer has not enough balance to perform debit")
        decrementBalance(id, amount)
    }

    @SqlQuery("SELECT * FROM customers")
    fun getAll(): List<Customer>

    @SqlQuery("SELECT balance FROM reserved_balances WHERE userId = :id AND orderId = :orderId")
    fun getReservedBalanceAmountByUserIdAndOrderId(
        @Bind("id") id: Int,
        @Bind("orderId") orderId: Int,
    ): Int?

    @SqlQuery("SELECT * FROM customers WHERE id = :id")
    fun findById(@Bind("id") id: Int): Customer?

}