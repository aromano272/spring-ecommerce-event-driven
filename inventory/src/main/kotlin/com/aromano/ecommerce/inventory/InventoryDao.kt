package com.aromano.ecommerce.inventory

import com.aromano.ecommerce.common.Cents
import com.aromano.ecommerce.inventory.domain.Product
import com.aromano.ecommerce.inventory.domain.ReserveProduct
import org.jdbi.v3.sqlobject.customizer.Bind
import org.jdbi.v3.sqlobject.kotlin.RegisterKotlinMapper
import org.jdbi.v3.sqlobject.kotlin.RegisterKotlinMappers
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys
import org.jdbi.v3.sqlobject.statement.SqlQuery
import org.jdbi.v3.sqlobject.statement.SqlUpdate
import org.jdbi.v3.sqlobject.transaction.Transaction
import org.springframework.stereotype.Repository

@Repository
@RegisterKotlinMappers(
    RegisterKotlinMapper(Product::class),
    RegisterKotlinMapper(ReserveProduct::class),
)
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

    @SqlUpdate("UPDATE products SET reservedInventory = reservedInventory + :amount WHERE id = :id")
    fun incrementReservedInventory(
        @Bind("id") id: Int,
        @Bind("amount") amount: Int,
    )

    @SqlUpdate("UPDATE products SET reservedInventory = reservedInventory - :amount WHERE id = :id")
    fun decrementReservedInventory(
        @Bind("id") id: Int,
        @Bind("amount") amount: Int,
    )

    @SqlUpdate("""
        INSERT INTO reserved_products (productId, userId, orderId, price, quantity)
        VALUES (:productId, :userId, :orderId, :price, :quantity)
    """)
    fun insertReservedInventoryForOrder(
        @Bind("productId") productId: Int,
        @Bind("userId") userId: Int,
        @Bind("orderId") orderId: Int,
        @Bind("price") price: Cents,
        @Bind("quantity") quantity: Int,
    )

    @SqlUpdate("DELETE FROM reserved_products WHERE user_id = :userId AND order_id = :orderId")
    fun deleteReservedInventoryForOrder(
        @Bind("userId") userId: Int,
        @Bind("orderId") orderId: Int,
    )

    @Transaction
    fun validateAndReserveInventoryForOrderTx(
        userId: Int,
        orderId: Int,
        reserveProducts: List<ReserveProduct>,
        validate: (Product) -> Unit,
    ) {
        val productsById = reserveProducts.associateBy { it.productId }
        val productIds = productsById.keys
        val products = findAllById(productIds)
        if (products.size != productIds.size) throw IllegalArgumentException("Missing product ids: ${productIds - products.map { it.id }}")

        products.forEach { product ->
            validate(product)
            val quantity = productsById[product.id]!!.quantity
            insertReservedInventoryForOrder(
                productId = product.id,
                userId = userId,
                orderId = orderId,
                price = product.price,
                quantity = quantity,
            )
            incrementReservedInventory(product.id, quantity)
        }
    }

    @Transaction
    fun submitReservedInventoryForOrderTx(userId: Int, orderId: Int) {
        val reservedProducts = findReservedByUserIdAndOrderId(userId, orderId)
        val quantityByProductId = reservedProducts.associateBy { it.productId }
            .mapValues { (_, product) -> product.quantity }

        deleteReservedInventoryForOrder(userId, orderId)
        quantityByProductId.forEach { (id, quantity) ->
            decrementReservedInventory(id, quantity)
            decrementInventory(id, quantity)
        }
    }

    @Transaction
    fun releaseReservedInventoryForOrderTx(userId: Int, orderId: Int) {
        val reservedProducts = findReservedByUserIdAndOrderId(userId, orderId)
        val quantityByProductId = reservedProducts.associateBy { it.productId }
            .mapValues { (_, product) -> product.quantity }

        deleteReservedInventoryForOrder(userId, orderId)
        quantityByProductId.forEach { (id, quantity) ->
            decrementReservedInventory(id, quantity)
        }
    }

    @SqlQuery("SELECT * FROM products")
    fun getAll(): List<Product>

    @SqlQuery("SELECT * FROM reserved_products WHERE userId = :userId AND orderId = :orderId")
    fun findReservedByUserIdAndOrderId(
        @Bind("userId") userId: Int,
        @Bind("orderId") orderId: Int,
    ): List<ReserveProduct>

    @SqlQuery("SELECT * FROM products WHERE id = :id")
    fun findById(@Bind("id") id: Int): Product?

    @SqlQuery("SELECT * FROM products WHERE id = ANY(:ids)")
    fun findAllById(
        @Bind("ids") ids: Set<Int>,
    ): List<Product>

}