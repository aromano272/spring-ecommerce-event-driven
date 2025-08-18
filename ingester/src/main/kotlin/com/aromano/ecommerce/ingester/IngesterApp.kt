package com.aromano.ecommerce.ingester

import com.aromano.ecommerce.common.AmqpDef
import com.aromano.ecommerce.common.config.BaseAmqpConfig
import com.aromano.ecommerce.common.domain.Totals
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.amqp.core.FanoutExchange
import org.springframework.amqp.core.Message
import org.springframework.amqp.core.Queue
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.ResponseEntity
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.atomic.AtomicReference
import java.util.function.UnaryOperator
import kotlin.jvm.java
import kotlin.math.max

@Configuration
class AmqpConfig : BaseAmqpConfig()

@SpringBootApplication
class IngesterApp {

    @Bean
    fun exchange(): FanoutExchange =
        FanoutExchange(AmqpDef.INGESTER_EXCHANGE_FANOUT, true, false)

    @Bean
    fun queueReadyToTransform(): Queue =
        Queue(AmqpDef.READY_TO_TRANSFORM_QUEUE, true, false, false)

}

@RestController
@RequestMapping("/ingester")
class IngesterRoutes(
    private val listener: ReadyToIngestListener,
) {

    @GetMapping("/total")
    fun getTotal(): ResponseEntity<Totals> = ResponseEntity.ok(listener.totals.getAndUpdate { it.copy(emittedCount = 0) })

}

@Service
class ReadyToIngestListener(
    private val template: RabbitTemplate,
) {

    private val logger: Logger = LoggerFactory.getLogger(ReadyToIngestListener::class.java)

    val totals = AtomicReference(Totals())

    @KafkaListener(topics = ["topic-provider"])
    // TODO(aromano): test with suspend
    fun listener(payload: String) {
        val payload = payload.trim { it == '"' }
        val id = payload.trim { it == '"' }.toLong()

        totals.getAndUpdate {
            Totals(
                emittedCount = it.emittedCount + 1,
                maxEmittedId = max(it.maxEmittedId, id),
//                emittedIds = it.emittedIds + id,
            )
        }

        logger.info("Received message $payload")

        val now = System.currentTimeMillis()
        val message = "$id:$now"
        template.send(
            AmqpDef.INGESTER_EXCHANGE_FANOUT,
            "",
            Message(message.toByteArray()),
        )
        logger.info("Sent message $message")
    }

}

fun main(args: Array<String>) {
    runApplication<IngesterApp>(*args)
}