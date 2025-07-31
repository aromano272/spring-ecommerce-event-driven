package com.aromano.ecommerce.admindashboard.controller

import com.aromano.ecommerce.admindashboard.events.ReserveBalanceFailed
import com.aromano.ecommerce.admindashboard.events.ReserveBalanceSuccess
import com.aromano.ecommerce.admindashboard.events.DecrementIntentoryFailed
import com.aromano.ecommerce.admindashboard.events.InventoryDecrementSuccess
import com.fasterxml.jackson.databind.JsonNode
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

@RestController
@RequestMapping("/api")
class AdminController(
    private val kafkaTemplate: KafkaTemplate<String, Any>,
    private val restTemplate: RestTemplate,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(AdminController::class.java)

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
        @RequestParam sagaId: String
    ): ResponseEntity<String> {
        logger.info("Emitting InventoryDecrementSuccess event for orderId: $orderId, sagaId: $sagaId")

        val event = InventoryDecrementSuccess(sagaId = sagaId, orderId = orderId)
        kafkaTemplate.send("inventory-events", event.orderId.toString(), event)

        return ResponseEntity.ok("Event sent: ${event.eventType} for orderId: ${event.orderId}, sagaId: ${event.sagaId}")
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

        val event = DecrementIntentoryFailed(sagaId = sagaId, orderId = orderId, error = "some error")
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
}
