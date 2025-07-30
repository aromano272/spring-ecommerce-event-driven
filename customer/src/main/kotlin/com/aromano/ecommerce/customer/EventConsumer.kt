package com.aromano.ecommerce.customer

import com.aromano.ecommerce.common.Cents
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.config.TopicBuilder
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Component
import kotlin.jvm.java
import kotlin.reflect.KClass

@Component
class EventConsumer(
    private val service: CustomerService,
    private val objectMapper: ObjectMapper,
) {

    private val logger = LoggerFactory.getLogger(EventConsumer::class.java)

    @Bean
    fun topicCustomerCommands() = TopicBuilder.name("customer-commands").build()

    @KafkaListener(topics = ["customer-commands"], groupId = "costumer-commands")
    fun handleCustomerCommands(@Payload payload: String) {
        logger.info("handleCustomerCommands payload: $payload")
        val event: KafkaEvent = objectMapper.toValue(
            payload,
            DecrementBalanceCommand::class,
            RollbackDecrementBalanceCommand::class,
        ) ?: return

        when (event) {
            is DecrementBalanceCommand -> {
                service.decrementBalanceForOrder(event)
            }
            is RollbackDecrementBalanceCommand -> {
                service.incrementBalance(event.userId, event.amount)
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

data class DecrementBalanceCommand(
    override val sagaId: String,
    val orderId: Int,
    val userId: Int,
    val amount: Cents,
) : KafkaEvent()

data class RollbackDecrementBalanceCommand(
    override val sagaId: String,
    val orderId: Int,
    val userId: Int,
    val amount: Cents,
) : KafkaEvent()

