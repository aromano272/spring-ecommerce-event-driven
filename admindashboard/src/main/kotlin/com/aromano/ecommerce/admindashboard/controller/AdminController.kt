package com.aromano.ecommerce.admindashboard.controller

import com.aromano.ecommerce.admindashboard.events.ReserveBalanceFailed
import com.aromano.ecommerce.admindashboard.events.ReserveBalanceSuccess
import com.aromano.ecommerce.admindashboard.events.ReserveInventoryFailed
import com.aromano.ecommerce.admindashboard.events.ReserveInventorySuccess
import com.aromano.ecommerce.admindashboard.events.SubmitReservedBalanceSuccess
import com.aromano.ecommerce.admindashboard.events.SubmitReservedBalanceFailed
import com.aromano.ecommerce.admindashboard.events.ReleasedReservedBalanceSuccess
import com.aromano.ecommerce.admindashboard.events.ReleasedReservedBalanceFailed
import com.aromano.ecommerce.common.domain.Totals
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientImpl
import com.github.dockerjava.okhttp.OkDockerHttpClient
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.jvm.java

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
    private var getTotalsStart = 0L
    private var totalProvided: Totals = Totals()
    private var totalIngested: Totals = Totals()
    private var totalTransformed: Totals = Totals()
    private var totalDispatched: Totals = Totals()
    val dispatchedIds = ConcurrentHashMap.newKeySet<String>()


    // Buffer for collecting messages before sending them in batches
    private val messageBuffer = CopyOnWriteArrayList<String>()

    // Scheduler for sending batched messages
    private val sseScheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val totalsScheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

    private val docker: DockerClient = run {
        val cfg = DefaultDockerClientConfig.createDefaultConfigBuilder()
            .build()
        val http = OkDockerHttpClient.Builder()
            .dockerHost(cfg.dockerHost)
            .sslConfig(cfg.sslConfig)
            .build()
        DockerClientImpl.getInstance(cfg, http)
    }

    init {
        // Schedule task to send batched messages every 100ms
        sseScheduler.scheduleAtFixedRate(this::sendBatchedMessages, 0, 100, TimeUnit.MILLISECONDS)
        totalsScheduler.scheduleAtFixedRate(this::getTotals, 0, 500, TimeUnit.MILLISECONDS)
    }

    data class SSEPayload(
        val messages: List<String>,
        val totalProvided: Totals?,
        val totalIngested: Totals?,
        val totalTransformed: Totals?,
        val totalDispatched: Totals?,
        val totalProvidedCount: Int,
        val totalDispatchedCount: Int,
    )

    private fun sendBatchedMessages() {
        // Create a copy of the buffer and clear it
        val messagesToSend = ArrayList(messageBuffer)
        messageBuffer.clear()

        // Send the batched messages to all connected clients
        val failedEmitters = mutableListOf<SseEmitter>()

        emitters.forEach { emitter ->
            try {
                val delta = ((System.currentTimeMillis() - getTotalsStart) / 1000)
                    .coerceAtLeast(1)
                    .toInt()
                val payload = SSEPayload(
                    messages = messagesToSend,
                    totalProvided = totalProvided.copy(emissionsPerSec = totalProvided.emittedCount / delta),
                    totalIngested = totalIngested.copy(emissionsPerSec = totalIngested.emittedCount / delta),
                    totalTransformed = totalTransformed.copy(emissionsPerSec = totalTransformed.emittedCount / delta),
                    totalDispatched = totalDispatched.copy(emissionsPerSec = totalDispatched.emittedCount / delta),
                    totalProvidedCount = totalProvided.emittedCount,
                    totalDispatchedCount = dispatchedIds.size,
                )
                val event = SseEmitter.event()
                    .name("messages")
                    .data(payload)
                emitter.send(event)
            } catch (e: Exception) {
                logger.error("Error sending batched messages to client: ${e.message}")
                failedEmitters.add(emitter)
            }
        }

        // Remove failed emitters
        emitters.removeAll(failedEmitters)
    }

    private fun getTotals() {
        if (getTotalsStart == 0L) getTotalsStart = System.currentTimeMillis()
        val providerUrl = "http://provider:8084/provider/total"
        val dispatcherUrl = "http://dispatcher:8081/dispatcher/total"
        val ingesterUrl = "http://ingester:8082/ingester/total"
        val transformerUrl = "http://transformer:8083/transformer/total"
        try {
            val start = System.currentTimeMillis()
            val response = restTemplate.getForEntity(providerUrl, Totals::class.java)
            response.body?.let {
                totalProvided = totalProvided.copy(
                    emittedCount = totalProvided.emittedCount + it.emittedCount,
                    maxEmittedId = it.maxEmittedId,
//                    emittedIds = totalProvided.emittedIds + it.emittedIds,
                )
            }
            val delta = System.currentTimeMillis() - start
            logger.info("provider totals took: ${delta}ms $totalProvided")
        } catch (e: Exception) {
            logger.error("Error getting provider totals: ${e.message}")
        }
        try {
            val start = System.currentTimeMillis()
            val response = restTemplate.getForEntity(dispatcherUrl, Totals::class.java)
            response.body?.let {
                totalDispatched = totalDispatched.copy(
                    emittedCount = totalDispatched.emittedCount + it.emittedCount,
                    maxEmittedId = it.maxEmittedId,
//                    emittedIds = totalDispatched.emittedIds + it.emittedIds,
                )
            }
            val delta = System.currentTimeMillis() - start
            logger.info("dispatcher totals took: ${delta}ms $totalDispatched")
        } catch (e: Exception) {
            logger.error("Error getting dispatcher totals: ${e.message}")
        }
        try {
            val start = System.currentTimeMillis()
            val response = restTemplate.getForEntity(ingesterUrl, Totals::class.java)
            response.body?.let {
                totalIngested = totalIngested.copy(
                    emittedCount = totalIngested.emittedCount + it.emittedCount,
                    maxEmittedId = it.maxEmittedId,
//                    emittedIds = totalIngested.emittedIds + it.emittedIds,
                )
            }
            val delta = System.currentTimeMillis() - start
            logger.info("ingester totals took: ${delta}ms $totalIngested")
        } catch (e: Exception) {
            logger.error("Error getting ingester totals: ${e.message}")
        }
        try {
            val start = System.currentTimeMillis()
            val response = restTemplate.getForEntity(transformerUrl, Totals::class.java)
            response.body?.let {
                totalTransformed = totalTransformed.copy(
                    emittedCount = totalTransformed.emittedCount + it.emittedCount,
                    maxEmittedId = it.maxEmittedId,
//                    emittedIds = totalTransformed.emittedIds + it.emittedIds,
                )
            }
            val delta = System.currentTimeMillis() - start
            logger.info("transformer totals took: ${delta}ms $totalTransformed")
        } catch (e: Exception) {
            logger.error("Error getting transformer totals: ${e.message}")
        }
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

    private fun findContainerId(name: String): String? {
        val list = docker.listContainersCmd().withShowAll(true).exec()
        val match = list.find { container ->
            container.names.any { it.trim('/').equals(name, true) }
        }
        return match?.id
    }

    private fun startContainer(name: String): ResponseEntity<String> {
        val id = findContainerId(name)
            ?: return ResponseEntity.badRequest().body("$name container not found")
        try {
            docker.startContainerCmd(id).exec()
            return ResponseEntity.ok(null)
        } catch (ex: Exception) {
            ex.printStackTrace()
            return ResponseEntity.badRequest().body("$name container failed to start")
        }
    }

    private fun stopContainer(name: String): ResponseEntity<String> {
        val id = findContainerId(name)
            ?: return ResponseEntity.badRequest().body("$name container not found")
        try {
            docker.stopContainerCmd(id).exec()
            return ResponseEntity.ok(null)
        } catch (ex: Exception) {
            ex.printStackTrace()
            return ResponseEntity.badRequest().body("$name container failed to stop")
        }
    }

    @PostMapping("/provider/start")
    fun startProvider(@RequestParam delay: Int): ResponseEntity<String> {
        logger.info("Starting provider with delay: $delay")

        val providerUrl = "http://provider:8084/provider/start?delay=$delay"
        try {
            restTemplate.postForEntity(providerUrl, null, String::class.java)
            return ResponseEntity.ok("provider start ingestion set to: $delay")
        } catch (e: Exception) {
            logger.error("Error setting provider start ingestion: ${e.message}")
            return ResponseEntity.badRequest().body("Error setting provider start ingestion: ${e.message}")
        }
    }

    @PostMapping("/provider/stop")
    fun stopProvider(): ResponseEntity<String> {
        logger.info("stoping provider with delay")

        val providerUrl = "http://provider:8084/provider/stop"
        try {
            restTemplate.postForEntity(providerUrl, null, String::class.java)
            return ResponseEntity.ok("provider stop ingestion")
        } catch (e: Exception) {
            logger.error("Error setting provider stop ingestion: ${e.message}")
            return ResponseEntity.badRequest().body("Error setting provider stop ingestion: ${e.message}")
        }
    }

    @PostMapping("/ingester/start")
    fun startIngester(): ResponseEntity<String> {
        logger.info("Starting ingester")

        return startContainer("ecommerce-ingester")
    }

    @PostMapping("/ingester/stop")
    fun stopIngester(): ResponseEntity<String> {
        logger.info("Stopping ingester")

        return stopContainer("ecommerce-ingester")
    }

    @PostMapping("/transformer/work-sleep")
    fun setTransformerWorkSleep(@RequestParam delay: Long): ResponseEntity<String> {
        logger.info("Setting transformer work sleep to: $delay")

        val transformerUrl = "http://transformer:8083/transformer/work-sleep?delay=$delay"
        try {
            restTemplate.postForEntity(transformerUrl, null, String::class.java)
            return ResponseEntity.ok("Transformer work sleep set to: $delay")
        } catch (e: Exception) {
            logger.error("Error setting transformer work sleep: ${e.message}")
            return ResponseEntity.badRequest().body("Error setting transformer work sleep: ${e.message}")
        }
    }

    @PostMapping("/transformer/start")
    fun startTransformer(): ResponseEntity<String> {
        logger.info("Starting transformer service")

        return startContainer("ecommerce-transformer")
    }

    @PostMapping("/transformer/stop")
    fun stopTransformer(): ResponseEntity<String> {
        logger.info("Stopping transformer service")

        return stopContainer("ecommerce-transformer")
    }

    @PostMapping("/dispatcher/start")
    fun startDispatcher(): ResponseEntity<String> {
        logger.info("Starting dispatcher service")

        return startContainer("ecommerce-dispatcher")
    }

    @PostMapping("/dispatcher/stop")
    fun stopDispatcher(): ResponseEntity<String> {
        logger.info("Stopping dispatcher service")

        return stopContainer("ecommerce-dispatcher")
    }

    @PostMapping("/kafka/start")
    fun startKafka(): ResponseEntity<String> {
        logger.info("Starting Kafka service")

        return startContainer("kafka")
    }

    @PostMapping("/kafka/stop")
    fun stopKafka(): ResponseEntity<String> {
        logger.info("Stopping Kafka service")

        return stopContainer("kafka")
    }

    @PostMapping("/rabbitmq/start")
    fun startRabbitmq(): ResponseEntity<String> {
        logger.info("Starting RabbitMQ service")

        return startContainer("ecommerce-rabbitmq")
    }

    @PostMapping("/rabbitmq/stop")
    fun stopRabbitmq(): ResponseEntity<String> {
        logger.info("Stopping RabbitMQ service")

        return stopContainer("ecommerce-rabbitmq")
    }

    @PostMapping("/postgres/start")
    fun startPostgres(): ResponseEntity<String> {
        logger.info("Starting PostgreSQL service")

        return startContainer("ecommerce-postgres")
    }

    @PostMapping("/postgres/stop")
    fun stopPostgres(): ResponseEntity<String> {
        logger.info("Stopping PostgreSQL service")

        return stopContainer("ecommerce-postgres")
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
    fun decrementIntentoryFailed(
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
