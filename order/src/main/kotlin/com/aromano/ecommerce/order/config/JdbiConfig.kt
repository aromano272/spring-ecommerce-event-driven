package com.aromano.ecommerce.order.config

import com.aromano.ecommerce.order.OrderDao
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.kotlin.KotlinPlugin
import org.jdbi.v3.jackson2.Jackson2Config
import org.jdbi.v3.jackson2.Jackson2Plugin
import org.jdbi.v3.json.internal.JsonArgumentFactory
import org.jdbi.v3.json.internal.JsonColumnMapperFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.sql.DataSource
import kotlin.jvm.java

@Configuration
class JdbiConfig {
    @Bean
    fun jdbi(dataSource: DataSource): Jdbi = Jdbi.create(dataSource).apply {
        installPlugins()
        installPlugin(KotlinPlugin(enableCoroutineSupport = true))
        installPlugin(Jackson2Plugin())
        getConfig(Jackson2Config::class.java).mapper = jacksonObjectMapper()
    }

    @Bean
    fun orderDao(jdbi: Jdbi): OrderDao {
        return jdbi.onDemand(OrderDao::class.java)
    }

}
