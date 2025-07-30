package com.aromano.ecommerce.inventory.config

import com.aromano.ecommerce.common.config.BaseJdbiConfig
import com.aromano.ecommerce.inventory.InventoryDao
import org.jdbi.v3.core.Jdbi
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import kotlin.jvm.java

@Configuration
class JdbiConfig : BaseJdbiConfig() {

    @Bean
    fun inventoryDao(jdbi: Jdbi): InventoryDao {
        return jdbi.onDemand(InventoryDao::class.java)
    }

}
