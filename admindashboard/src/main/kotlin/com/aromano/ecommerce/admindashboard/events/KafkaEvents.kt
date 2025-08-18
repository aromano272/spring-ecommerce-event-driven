package com.aromano.ecommerce.admindashboard.events

import com.aromano.ecommerce.common.KafkaRef
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.handler.annotation.Header
import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

abstract class KafkaEvent {
    abstract val sagaId: String
    val eventType: String = this::class.simpleName.orEmpty()
}

data class ReserveInventorySuccess(
    override val sagaId: String,
    val orderId: Int,
    val totalCost: Int
) : KafkaEvent()

data class ReserveInventoryFailed(
    override val sagaId: String,
    val orderId: Int,
    val error: String
) : KafkaEvent()

data class ReserveBalanceSuccess(
    override val sagaId: String,
    val orderId: Int
) : KafkaEvent()

data class ReserveBalanceFailed(
    override val sagaId: String,
    val orderId: Int,
    val error: String
) : KafkaEvent()

data class SubmitReservedBalanceSuccess(
    override val sagaId: String,
    val orderId: Int
) : KafkaEvent()

data class SubmitReservedBalanceFailed(
    override val sagaId: String,
    val orderId: Int,
    val error: String
) : KafkaEvent()

data class ReleasedReservedBalanceSuccess(
    override val sagaId: String,
    val orderId: Int
) : KafkaEvent()

data class ReleasedReservedBalanceFailed(
    override val sagaId: String,
    val orderId: Int,
    val error: String
) : KafkaEvent()

@Component
class KafkaDispatchListener(
    private val adminController: com.aromano.ecommerce.admindashboard.controller.AdminController
) {
    private val logger = LoggerFactory.getLogger(KafkaDispatchListener::class.java)
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

    @KafkaListener(topics = ["topic-dispatch"])
    fun listen(
        message: String,
        @Header(KafkaHeaders.RECEIVED_KEY) key: String?
    ) {
//        logger.info("Received message $message")
        adminController.dispatchedIds += key

        adminController.addDispatchMessage(message)
    }
}
