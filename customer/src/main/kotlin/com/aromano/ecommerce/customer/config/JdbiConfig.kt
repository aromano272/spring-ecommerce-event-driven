package com.aromano.ecommerce.customer.config

import com.aromano.ecommerce.common.config.BaseJdbiConfig
import com.aromano.ecommerce.customer.CustomerDao
import org.jdbi.v3.core.Jdbi
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import kotlin.jvm.java

@Configuration
class JdbiConfig : BaseJdbiConfig() {

    @Bean
    fun customerDao(jdbi: Jdbi): CustomerDao {
        return jdbi.onDemand(CustomerDao::class.java)
    }

}
