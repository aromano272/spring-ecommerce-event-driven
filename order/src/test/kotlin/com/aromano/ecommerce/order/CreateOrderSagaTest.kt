package com.aromano.ecommerce.order

import com.aromano.ecommerce.order.domain.Product
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertInstanceOf
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.test.utils.KafkaTestUtils
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.kafka.KafkaContainer
import org.testcontainers.utility.DockerImageName
import java.util.concurrent.TimeUnit

@Testcontainers
@SpringBootTest
class CreateOrderSagaTest {

    companion object {
        @Container
        val kafka = KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0").asCompatibleSubstituteFor("apache/kafka"))

        @DynamicPropertySource
        fun kafkaProps(registry: DynamicPropertyRegistry) {
            registry.add("spring.kafka.bootstrap-servers") { kafka.bootstrapServers }
        }
    }

    @Autowired
    lateinit var kafkaTemplate: KafkaTemplate<String, Any>

    @Autowired
    lateinit var orderService: OrderService

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var sagaRunner: SagaRunner

    @Test
    fun `saga completes successfully on simulated events`() {
        val saga = CreateOrderSaga(kafkaTemplate, orderService, objectMapper)
        val products = listOf(
            Product(1, "name 1", 120),
            Product(2, "name 2", 220),
        )

        val result = sagaRunner.start(saga, CreateOrderSagaArgs(1, products))

        kafkaTemplate.send(
            "inventory-events",
            """
                {
                    "eventType": "InventoryDecrementSuccess",
                    "orderId": 1
            """.trimIndent(),
        )

        kafkaTemplate.send(
            "customer-events",
            """
                {
                    "eventType": "BalanceDecrementSuccess",
                    "orderId": 1
            """.trimIndent(),
        )

        assertInstanceOf<CreateOrderSagaResult>(result.get(5, TimeUnit.SECONDS))
    }
}