package com.aromano.ecommerce.admindashboard.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.KafkaOperations
import org.springframework.kafka.listener.CommonErrorHandler
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.util.backoff.FixedBackOff

@Configuration
class KafkaConfig {
    @Bean
    fun errorHandler(template: KafkaOperations<Any?, Any?>): CommonErrorHandler {
        return DefaultErrorHandler(
            DeadLetterPublishingRecoverer(template), FixedBackOff(1000L, 2)
        )
    }
}