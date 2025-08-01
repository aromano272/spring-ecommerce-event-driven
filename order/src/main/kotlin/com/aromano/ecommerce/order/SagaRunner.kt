package com.aromano.ecommerce.order

import com.aromano.ecommerce.common.domain.UnhandledEventException
import com.aromano.ecommerce.order.ReleasedReservedInventoryFailed
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.config.TopicBuilder
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

enum class SagaState {
    CREATED,
    RUNNING,
    ROLLING_BACK,
    COMPLETED_SUCCESS,
    COMPLETED_FAILED,
}

interface Saga<Args, Result> {
    val uuid: String
    fun start(args: Args): CompletableFuture<Result>
    fun handleEvent(event: KafkaEvent)
}

@Component
class SagaRunner(
    private val objectMapper: ObjectMapper,
) {
    private val logger = LoggerFactory.getLogger(SagaRunner::class.java)

    private val mem = ConcurrentHashMap<String, Saga<*, *>>()

    @Async
    fun <Args, Result> start(saga: Saga<Args, Result>, args: Args): CompletableFuture<Result> {
        logger.info("start thread: ${Thread.currentThread()}")
        mem.put(saga.uuid, saga)
        return saga.start(args).whenComplete { _, _ ->
            mem.remove(saga.uuid)
        }
    }

    @Bean
    fun topicInventoryEvents() = TopicBuilder.name("inventory-events").build()

    @Bean
    fun topicInventoryCommands() = TopicBuilder.name("inventory-commands").build()

    @Bean
    fun topicCustomerEvents() = TopicBuilder.name("customer-events").build()

    @Bean
    fun topicCustomerCommands() = TopicBuilder.name("customer-commands").build()

    @KafkaListener(topics = ["inventory-events"], groupId = "saga-runner")
    fun handleInventoryEvents(@Payload payload: String) {
        logger.info("handleInventoryEvents thread: ${Thread.currentThread()}")
        logger.info("handleInventoryEvents payload: $payload")
        val event: KafkaEvent = objectMapper.toValue(
            payload,
            ReserveInventorySuccess::class,
            ReserveInventoryFailed::class,
            SubmitReservedInventorySuccess::class,
            SubmitReservedInventoryFailed::class,
            ReleasedReservedInventorySuccess::class,
            ReleasedReservedInventoryFailed::class,
        ) ?: return

        mem[event.sagaId]?.handleEvent(event)
    }

    @KafkaListener(topics = ["customer-events"], groupId = "saga-runner")
    fun handleCustomerEvents(@Payload payload: String) {
        logger.info("handleCustomerEvents thread: ${Thread.currentThread()}")
        logger.info("handleCustomerEvents payload: $payload")
        val event: KafkaEvent = objectMapper.toValue(
            payload,
            ReserveBalanceSuccess::class,
            ReserveBalanceFailed::class,
            SubmitReservedBalanceSuccess::class,
            SubmitReservedBalanceFailed::class,
            ReleasedReservedBalanceSuccess::class,
            ReleasedReservedBalanceFailed::class,
        ) ?: return

        mem[event.sagaId]?.handleEvent(event)
    }

}