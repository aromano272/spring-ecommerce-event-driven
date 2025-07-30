package com.aromano.ecommerce.customer

import com.aromano.ecommerce.common.Cents
import com.aromano.ecommerce.customer.domain.Customer
import org.jdbi.v3.sqlobject.customizer.Bind
import org.jdbi.v3.sqlobject.kotlin.RegisterKotlinMapper
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys
import org.jdbi.v3.sqlobject.statement.SqlQuery
import org.jdbi.v3.sqlobject.statement.SqlUpdate
import org.jdbi.v3.sqlobject.transaction.Transaction
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Repository
import org.springframework.web.server.ResponseStatusException

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

    @Transaction
    fun decrementBalanceIfPossibleTx(id: Int, amount: Cents) {
        val customer = findById(id) ?: throw IllegalArgumentException("customer with id $id not found")
        if (customer.balance < amount) throw IllegalArgumentException("customer has not enough balance to perform debit")
        decrementBalance(id, amount)
    }

    @SqlQuery("SELECT * FROM customers")
    fun getAll(): List<Customer>

    @SqlQuery("SELECT * FROM customers WHERE id = :id")
    fun findById(@Bind("id") id: Int): Customer?

}