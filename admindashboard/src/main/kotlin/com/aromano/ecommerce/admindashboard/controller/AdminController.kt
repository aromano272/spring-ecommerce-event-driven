package com.aromano.ecommerce.admindashboard.controller

import com.aromano.ecommerce.admindashboard.events.ReserveBalanceFailed
import com.aromano.ecommerce.admindashboard.events.ReserveBalanceSuccess
import com.aromano.ecommerce.admindashboard.events.ReserveInventoryFailed
import com.aromano.ecommerce.admindashboard.events.ReserveInventorySuccess
import com.aromano.ecommerce.admindashboard.events.SubmitReservedBalanceSuccess
import com.aromano.ecommerce.admindashboard.events.SubmitReservedBalanceFailed
import com.aromano.ecommerce.admindashboard.events.ReleasedReservedBalanceSuccess
import com.aromano.ecommerce.admindashboard.events.ReleasedReservedBalanceFailed
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.RestTemplate
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

@RestController
@RequestMapping("/api")
class AdminController(
    private val kafkaTemplate: KafkaTemplate<String, Any>,
    private val restTemplate: RestTemplate,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(AdminController::class.java)

    private val emitters = CopyOnWriteArrayList<SseEmitter>()
    private val dispatchMessages = mutableListOf<String>()

    // Buffer for collecting messages before sending them in batches
    private val messageBuffer = CopyOnWriteArrayList<String>()

    // Scheduler for sending batched messages
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

    init {
        // Schedule task to send batched messages every 100ms
        scheduler.scheduleAtFixedRate(this::sendBatchedMessages, 0, 100, TimeUnit.MILLISECONDS)
    }

    private fun sendBatchedMessages() {
        if (messageBuffer.isEmpty() || emitters.isEmpty()) {
            return
        }

        // Create a copy of the buffer and clear it
        val messagesToSend = ArrayList(messageBuffer)
        messageBuffer.clear()

        // Send the batched messages to all connected clients
        val failedEmitters = mutableListOf<SseEmitter>()

        emitters.forEach { emitter ->
            try {
                val event = SseEmitter.event()
                    .name("messages")
                    .data(messagesToSend)
                emitter.send(event)
            } catch (e: Exception) {
                logger.error("Error sending batched messages to client: ${e.message}")
                failedEmitters.add(emitter)
            }
        }

        // Remove failed emitters
        emitters.removeAll(failedEmitters)
    }

    fun addDispatchMessage(message: String) {
        // Add message to the history
        dispatchMessages.add(message)
        // Keep only the last 100 messages
        if (dispatchMessages.size > 100) {
            dispatchMessages.removeAt(0)
        }

        // Add message to the buffer for batched sending
        messageBuffer.add(message)
    }

    @GetMapping("/events")
    fun events(): SseEmitter {
        val emitter = SseEmitter(Long.MAX_VALUE)
        emitters.add(emitter)

        emitter.onCompletion { emitters.remove(emitter) }
        emitter.onTimeout { emitters.remove(emitter) }

        // Send all existing messages to the new client
        if (dispatchMessages.isNotEmpty()) {
            try {
                val event = SseEmitter.event()
                    .name("messages")
                    .data(dispatchMessages)
                emitter.send(event)
            } catch (e: Exception) {
                logger.error("Error sending initial messages to client: ${e.message}")
                emitter.completeWithError(e)
            }
        }

        return emitter
    }

    @PostMapping("/ingester/start")
    fun startIngester(@RequestParam delay: Int): ResponseEntity<String> {
        logger.info("Starting ingester with delay: $delay")

        val ingesterUrl = "http://localhost:8081/ingester/start?delay=$delay"
        try {
            restTemplate.postForEntity(ingesterUrl, null, String::class.java)
            return ResponseEntity.ok("Ingester started with delay: $delay")
        } catch (e: Exception) {
            logger.error("Error starting ingester: ${e.message}")
            return ResponseEntity.badRequest().body("Error starting ingester: ${e.message}")
        }
    }

    @PostMapping("/ingester/stop")
    fun stopIngester(): ResponseEntity<String> {
        logger.info("Stopping ingester")

        val ingesterUrl = "http://localhost:8081/ingester/stop"
        try {
            restTemplate.postForEntity(ingesterUrl, null, String::class.java)
            return ResponseEntity.ok("Ingester stopped")
        } catch (e: Exception) {
            logger.error("Error stopping ingester: ${e.message}")
            return ResponseEntity.badRequest().body("Error stopping ingester: ${e.message}")
        }
    }

    @PostMapping("/transformer/work-sleep")
    fun setTransformerWorkSleep(@RequestParam delay: Long): ResponseEntity<String> {
        logger.info("Setting transformer work sleep to: $delay")

        val transformerUrl = "http://localhost:8083/transformer/work-sleep?delay=$delay"
        try {
            restTemplate.postForEntity(transformerUrl, null, String::class.java)
            return ResponseEntity.ok("Transformer work sleep set to: $delay")
        } catch (e: Exception) {
            logger.error("Error setting transformer work sleep: ${e.message}")
            return ResponseEntity.badRequest().body("Error setting transformer work sleep: ${e.message}")
        }
    }

    @PostMapping("/order/create")
    fun createOrder(@RequestParam userId: Int): ResponseEntity<String> {
        logger.info("Creating order for user: $userId")

        val orderServiceUrl = "http://localhost:8081/order?userId=$userId"
        val response = restTemplate.postForEntity(orderServiceUrl, null, String::class.java)

        // Extract orderId from response if possible
        val responseBody = response.body
        val orderId = if (responseBody != null) {
            try {
                val jsonNode = objectMapper.readTree(responseBody)
                jsonNode.get("orderId")?.asInt() ?: -1
            } catch (e: Exception) {
                logger.error("Failed to parse order response: ${e.message}")
                -1
            }
        } else {
            -1
        }

        // Return response with orderId information
        val enhancedResponse = if (orderId > 0) {
            "$responseBody\nExtracted orderId: $orderId"
        } else {
            responseBody ?: "No response body"
        }

        return ResponseEntity.status(response.statusCode).body(enhancedResponse)
    }

    @PostMapping("/events/inventory-decrement-success")
    fun inventoryDecrementSuccess(
        @RequestParam orderId: Int,
        @RequestParam sagaId: String,
        @RequestParam totalCost: Int
    ): ResponseEntity<String> {
        logger.info("Emitting InventoryDecrementSuccess event for orderId: $orderId, sagaId: $sagaId, totalCost: $totalCost")

        val event = ReserveInventorySuccess(sagaId = sagaId, orderId = orderId, totalCost = totalCost)
        kafkaTemplate.send("inventory-events", event.orderId.toString(), event)

        return ResponseEntity.ok("Event sent: ${event.eventType} for orderId: ${event.orderId}, sagaId: ${event.sagaId}, totalCost: ${event.totalCost}")
    }

    @PostMapping("/events/balance-decrement-success")
    fun reserveBalanceSuccess(
        @RequestParam orderId: Int,
        @RequestParam sagaId: String
    ): ResponseEntity<String> {
        logger.info("Emitting ReserveBalanceSuccess event for orderId: $orderId, sagaId: $sagaId")

        val event = ReserveBalanceSuccess(sagaId = sagaId, orderId = orderId)
        kafkaTemplate.send("customer-events", event.orderId.toString(), event)

        return ResponseEntity.ok("Event sent: ${event.eventType} for orderId: ${event.orderId}, sagaId: ${event.sagaId}")
    }

    @PostMapping("/events/inventory-decrement-failed")
    fun DecrementIntentoryFailed(
        @RequestParam orderId: Int,
        @RequestParam sagaId: String
    ): ResponseEntity<String> {
        logger.info("Emitting DecrementIntentoryFailed event for orderId: $orderId, sagaId: $sagaId")

        val event = ReserveInventoryFailed(sagaId = sagaId, orderId = orderId, error = "some error")
        kafkaTemplate.send("inventory-events", event.orderId.toString(), event)

        return ResponseEntity.ok("Event sent: ${event.eventType} for orderId: ${event.orderId}, sagaId: ${event.sagaId}, error: ${event.error}")
    }

    @PostMapping("/events/balance-decrement-failed")
    fun reserveBalanceFailed(
        @RequestParam orderId: Int,
        @RequestParam sagaId: String
    ): ResponseEntity<String> {
        logger.info("Emitting ReserveBalanceFailed event for orderId: $orderId, sagaId: $sagaId")

        val event = ReserveBalanceFailed(sagaId = sagaId, orderId = orderId, error = "some error")
        kafkaTemplate.send("customer-events", event.orderId.toString(), event)

        return ResponseEntity.ok("Event sent: ${event.eventType} for orderId: ${event.orderId}, sagaId: ${event.sagaId}, error: ${event.error}")
    }

    @GetMapping("/order/get")
    fun getOrder(@RequestParam orderId: Int): ResponseEntity<String> {
        logger.info("Getting order with ID: $orderId")

        val orderServiceUrl = "http://localhost:8081/order/$orderId"
        try {
            val response = restTemplate.getForEntity(orderServiceUrl, String::class.java)
            return ResponseEntity.status(response.statusCode).body(response.body)
        } catch (e: Exception) {
            logger.error("Error getting order: ${e.message}")
            return ResponseEntity.badRequest().body("Error getting order: ${e.message}")
        }
    }

    @PostMapping("/events/submit-reserved-balance-success")
    fun submitReservedBalanceSuccess(
        @RequestParam orderId: Int,
        @RequestParam sagaId: String
    ): ResponseEntity<String> {
        logger.info("Emitting SubmitReservedBalanceSuccess event for orderId: $orderId, sagaId: $sagaId")

        val event = SubmitReservedBalanceSuccess(sagaId = sagaId, orderId = orderId)
        kafkaTemplate.send("customer-events", event.orderId.toString(), event)

        return ResponseEntity.ok("Event sent: ${event.eventType} for orderId: ${event.orderId}, sagaId: ${event.sagaId}")
    }

    @PostMapping("/events/submit-reserved-balance-failed")
    fun submitReservedBalanceFailed(
        @RequestParam orderId: Int,
        @RequestParam sagaId: String
    ): ResponseEntity<String> {
        logger.info("Emitting SubmitReservedBalanceFailed event for orderId: $orderId, sagaId: $sagaId")

        val event = SubmitReservedBalanceFailed(sagaId = sagaId, orderId = orderId, error = "some error")
        kafkaTemplate.send("customer-events", event.orderId.toString(), event)

        return ResponseEntity.ok("Event sent: ${event.eventType} for orderId: ${event.orderId}, sagaId: ${event.sagaId}, error: ${event.error}")
    }

    @PostMapping("/events/released-reserved-balance-success")
    fun releasedReservedBalanceSuccess(
        @RequestParam orderId: Int,
        @RequestParam sagaId: String
    ): ResponseEntity<String> {
        logger.info("Emitting ReleasedReservedBalanceSuccess event for orderId: $orderId, sagaId: $sagaId")

        val event = ReleasedReservedBalanceSuccess(sagaId = sagaId, orderId = orderId)
        kafkaTemplate.send("customer-events", event.orderId.toString(), event)

        return ResponseEntity.ok("Event sent: ${event.eventType} for orderId: ${event.orderId}, sagaId: ${event.sagaId}")
    }

    @PostMapping("/events/released-reserved-balance-failed")
    fun releasedReservedBalanceFailed(
        @RequestParam orderId: Int,
        @RequestParam sagaId: String
    ): ResponseEntity<String> {
        logger.info("Emitting ReleasedReservedBalanceFailed event for orderId: $orderId, sagaId: $sagaId")

        val event = ReleasedReservedBalanceFailed(sagaId = sagaId, orderId = orderId, error = "some error")
        kafkaTemplate.send("customer-events", event.orderId.toString(), event)

        return ResponseEntity.ok("Event sent: ${event.eventType} for orderId: ${event.orderId}, sagaId: ${event.sagaId}, error: ${event.error}")
    }
}
