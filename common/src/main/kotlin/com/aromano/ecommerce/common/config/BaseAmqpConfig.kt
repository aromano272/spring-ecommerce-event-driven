package com.aromano.ecommerce.common.config

import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class BaseAmqpConfig {

    private val logger = LoggerFactory.getLogger(BaseAmqpConfig::class.java)

    @Bean
    fun rabbitListenerContainerFactory(
        configurer: SimpleRabbitListenerContainerFactoryConfigurer,
        connectionFactory: CachingConnectionFactory,
    ): SimpleRabbitListenerContainerFactory {
        val factory = SimpleRabbitListenerContainerFactory()
        configurer.configure(factory, connectionFactory)

        factory.setDefaultRequeueRejected(false)

        factory.setErrorHandler { err ->
            logger.error("Error consuming message, skipping message", err)
        }

        return factory
    }

}