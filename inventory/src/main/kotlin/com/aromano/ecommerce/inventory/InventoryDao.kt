package com.aromano.ecommerce.inventory

import com.aromano.ecommerce.inventory.domain.Product
import org.jdbi.v3.sqlobject.customizer.Bind
import org.jdbi.v3.sqlobject.kotlin.RegisterKotlinMapper
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys
import org.jdbi.v3.sqlobject.statement.SqlQuery
import org.jdbi.v3.sqlobject.statement.SqlUpdate
import org.jdbi.v3.sqlobject.transaction.Transaction
import org.springframework.stereotype.Repository

@Repository
@RegisterKotlinMapper(Product::class)
interface InventoryDao {

    @SqlUpdate("INSERT INTO products (name, price, inventory) VALUES ('some name', 10, 10)")
    @GetGeneratedKeys
    fun insert(): Int?

    @SqlUpdate("UPDATE products SET inventory = inventory + :amount WHERE id = :id")
    fun incrementInventory(
        @Bind("id") id: Int,
        @Bind("amount") amount: Int,
    )

    @SqlUpdate("UPDATE products SET inventory = inventory - :amount WHERE id = :id")
    fun decrementInventory(
        @Bind("id") id: Int,
        @Bind("amount") amount: Int,
    )

    @Transaction
    fun decrementInventoryIfPossibleTx(id: Int, amount: Int) {
        val product = findById(id) ?: throw IllegalArgumentException("product with id $id not found")
        if (product.inventory < amount) throw IllegalArgumentException("product has not enough inventory to perform decrement")
        decrementInventory(id, amount)
    }

    @SqlQuery("SELECT * FROM products")
    fun getAll(): List<Product>

    @SqlQuery("SELECT * FROM products WHERE id = :id")
    fun findById(@Bind("id") id: Int): Product?

}