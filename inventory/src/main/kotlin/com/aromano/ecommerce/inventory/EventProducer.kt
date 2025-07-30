package com.aromano.ecommerce.inventory

import com.aromano.ecommerce.common.Cents
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.cache.CacheManagerCustomizers
import org.springframework.context.annotation.Bean
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.config.TopicBuilder
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event
import kotlin.jvm.java
import kotlin.reflect.KClass

@Component
class EventProducer(
    private val kafkaTemplate: KafkaTemplate<String, Any>,
) {

    private val logger = LoggerFactory.getLogger(EventProducer::class.java)

    @Bean
    fun topicInventoryEvents() = TopicBuilder.name("inventory-events").build()

    fun send(
        key: String,
        value: KafkaEvent,
    ) {
        kafkaTemplate.send("inventory-events", key, value)
    }


}

data class InventoryDecrementSuccess(
    override val sagaId: String,
    val orderId: Int,
) : KafkaEvent()

data class InventoryDecrementFailed(
    override val sagaId: String,
    val orderId: Int,
    val error: String,
) : KafkaEvent()

