package com.aromano.ecommerce.inventory

import com.aromano.ecommerce.common.Cents
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.config.TopicBuilder
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Component
import kotlin.jvm.java
import kotlin.reflect.KClass

@Component
class EventConsumer(
    private val service: InventoryService,
    private val objectMapper: ObjectMapper,
) {

    private val logger = LoggerFactory.getLogger(EventConsumer::class.java)

    @Bean
    fun topicInventoryCommands() = TopicBuilder.name("inventory-commands").build()

    @KafkaListener(topics = ["inventory-commands"], groupId = "costumer-commands")
    fun handleInventoryCommands(@Payload payload: String) {
        logger.info("handleInventoryCommands payload: $payload")
        val event: KafkaEvent = objectMapper.toValue(
            payload,
            DecrementInventoryCommand::class,
            RollbackDecrementInventoryCommand::class,
        ) ?: return

        when (event) {
            is DecrementInventoryCommand -> {
                service.decrementInventoryForOrder(event)
            }
            is RollbackDecrementInventoryCommand -> {
                service.incrementInventory(event.userId, event.amount)
            }
        }
    }


}

inline fun <reified T : KafkaEvent> ObjectMapper.toValue(payload: String): T? {
    val node = readTree(payload)
    val eventType = node["eventType"]?.asText() ?: return null
    val match = T::class.simpleName == eventType
    if (!match) return null
    return treeToValue(node, T::class.java)
}

fun ObjectMapper.toValue(payload: String, vararg events: KClass<*>): KafkaEvent? {
    val node = readTree(payload)
    val eventType = node["eventType"]?.asText() ?: return null
    val match = events.find { event -> event.simpleName == eventType }
    return match?.let {
        treeToValue(node, it.java) as? KafkaEvent
    }
}

abstract class KafkaEvent {
    abstract val sagaId: String
    val eventType: String = this::class.simpleName.orEmpty()
}

data class DecrementInventoryCommand(
    override val sagaId: String,
    val orderId: Int,
    val userId: Int,
    val amount: Cents,
) : KafkaEvent()

data class RollbackDecrementInventoryCommand(
    override val sagaId: String,
    val orderId: Int,
    val userId: Int,
    val amount: Cents,
) : KafkaEvent()

