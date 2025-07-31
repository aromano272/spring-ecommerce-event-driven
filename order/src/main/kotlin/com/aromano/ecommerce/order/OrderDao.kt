package com.aromano.ecommerce.order

import com.aromano.ecommerce.order.domain.Order
import com.aromano.ecommerce.order.domain.OrderState
import com.aromano.ecommerce.order.domain.Product
import org.jdbi.v3.json.Json
import org.jdbi.v3.sqlobject.customizer.Bind
import org.jdbi.v3.sqlobject.kotlin.BindKotlin
import org.jdbi.v3.sqlobject.kotlin.RegisterKotlinMapper
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys
import org.jdbi.v3.sqlobject.statement.SqlQuery
import org.jdbi.v3.sqlobject.statement.SqlUpdate
import org.springframework.stereotype.Repository

@Repository
@RegisterKotlinMapper(Order::class)
interface OrderDao {

    @SqlUpdate("""
        INSERT INTO orders (userId, state, products)
        VALUES (:userId, :state, :products :: jsonb)
    """)
    @GetGeneratedKeys
    fun insert(
        @Bind("userId") userId: Int,
        @Bind("state") state: OrderState,
        @Json products: List<Product>,
    ): Int?

    @SqlUpdate("UPDATE orders SET state = :state WHERE id = :id")
    fun updateState(
        @Bind("id") id: Int,
        @Bind("state") state: OrderState,
    )

    @SqlQuery("SELECT * FROM orders WHERE user_id = :userId")
    fun getAllByUserId(@Bind("userId") userId: Int): List<Order>

    @SqlQuery("SELECT * FROM orders WHERE id = :id")
    fun findById(@Bind("id") id: Int): Order?

}