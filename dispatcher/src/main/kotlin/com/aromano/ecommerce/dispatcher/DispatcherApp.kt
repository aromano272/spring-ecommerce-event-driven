package com.aromano.ecommerce.dispatcher

import com.aromano.ecommerce.common.AmqpDef
import com.aromano.ecommerce.common.KafkaRef
import com.aromano.ecommerce.common.config.BaseAmqpConfig
import com.aromano.ecommerce.common.domain.Totals
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.FanoutExchange
import org.springframework.amqp.core.Message
import org.springframework.amqp.core.Queue
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.ResponseEntity
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.atomic.AtomicReference
import java.util.function.UnaryOperator
import kotlin.math.max

@Configuration
class AmqpConfig : BaseAmqpConfig()

@SpringBootApplication
class DispatcherApp {

    @Bean
    fun transformerExchange(): FanoutExchange =
        FanoutExchange(AmqpDef.TRANSFORMER_EXCHANGE_FANOUT, true, false)

    @Bean
    fun queueReadyToDispatch(): Queue =
        Queue(AmqpDef.READY_TO_DISPATCH_QUEUE, true, false, false)

    @Bean
    fun bindingReadyToDispatch(): Binding =
        BindingBuilder
            .bind(queueReadyToDispatch())
            .to(transformerExchange())

}

@Service
class ReadyToDispatchListener(
    val template: KafkaTemplate<String, String>,
) {

    private val logger: Logger = LoggerFactory.getLogger(ReadyToDispatchListener::class.java)

    val totals = AtomicReference(Totals())

    @RabbitListener(queues = ["ready-to-dispatch-queue"])
    // TODO(aromano): test with suspend
    fun listener(message: Message) {
        val body = message.body.toString(Charsets.UTF_8)
        val (id, ingestedAt, transformedAt) = body.split(":").map { it.toLong() }

        totals.getAndUpdate {
            Totals(
                emittedCount = it.emittedCount + 1,
                maxEmittedId = max(it.maxEmittedId, id),
//                emittedIds = it.emittedIds + id,
            )
        }

        logger.info("Received message $body")

        template.send(KafkaRef.TOPIC_DISPATCH, id.toString(), body)

        logger.info("Sent message $body")
    }

}

@RestController
@RequestMapping("/dispatcher")
class DispatcherController(
    private val listener: ReadyToDispatchListener,
) {

    @GetMapping("/total")
    fun getTotal(): ResponseEntity<Totals> = ResponseEntity.ok(listener.totals.getAndUpdate { it.copy(emittedCount = 0) })

}

fun main(args: Array<String>) {
    runApplication<DispatcherApp>(*args)
}