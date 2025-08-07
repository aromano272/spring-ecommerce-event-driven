package com.aromano.ecommerce.inventory

import com.aromano.ecommerce.common.Cents
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.kafka.config.TopicBuilder
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import kotlin.jvm.java

@Component
class EventProducer(
    private val kafkaTemplate: KafkaTemplate<String, Any>,
) {

    private val logger = LoggerFactory.getLogger(EventProducer::class.java)

    @Bean
    fun topicInventoryEvents() = TopicBuilder.name("inventory-events").partitions(10).build()

    fun send(
        key: String,
        value: KafkaEvent,
    ) {
        kafkaTemplate.send("inventory-events", key, value)
    }


}

data class ReserveInventorySuccess(
    override val sagaId: String,
    val orderId: Int,
    val totalCost: Cents,
) : KafkaEvent()

data class ReserveInventoryFailed(
    override val sagaId: String,
    val orderId: Int,
    val error: String,
) : KafkaEvent()

data class SubmitReservedInventorySuccess(
    override val sagaId: String,
    val orderId: Int,
) : KafkaEvent()

data class SubmitReservedInventoryFailed(
    override val sagaId: String,
    val orderId: Int,
    val error: String,
) : KafkaEvent()

data class ReleasedReservedInventorySuccess(
    override val sagaId: String,
    val orderId: Int,
) : KafkaEvent()

data class ReleasedReservedInventoryFailed(
    override val sagaId: String,
    val orderId: Int,
    val error: String,
) : KafkaEvent()

