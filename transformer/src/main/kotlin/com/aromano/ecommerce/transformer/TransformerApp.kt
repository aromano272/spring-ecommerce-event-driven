package com.aromano.ecommerce.transformer

import com.aromano.ecommerce.common.AmqpDef
import com.aromano.ecommerce.common.config.BaseAmqpConfig
import com.aromano.ecommerce.common.domain.Totals
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.amqp.core.AmqpTemplate
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
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.atomic.AtomicReference
import java.util.function.UnaryOperator
import kotlin.math.max

@Configuration
class AmqpConfig : BaseAmqpConfig()

@SpringBootApplication
class TransformerApp {

    @Bean
    fun ingesterExchange(): FanoutExchange =
        FanoutExchange(AmqpDef.INGESTER_EXCHANGE_FANOUT, true, false)

    @Bean
    fun transformerExchange(): FanoutExchange =
        FanoutExchange(AmqpDef.TRANSFORMER_EXCHANGE_FANOUT, true, false)

    @Bean
    fun queueReadyToTransform(): Queue =
        Queue(AmqpDef.READY_TO_TRANSFORM_QUEUE, true, false, false)

    @Bean
    fun queueReadyToDispatch(): Queue =
        Queue(AmqpDef.READY_TO_DISPATCH_QUEUE, true, false, false)

    @Bean
    fun bindingReadyToTransform(): Binding =
        BindingBuilder
            .bind(queueReadyToTransform())
            .to(ingesterExchange())

}

object Globals {
    var WORK_SLEEP = 100L
}

@RestController
@RequestMapping("/transformer")
class TransformerRoutes(
    private val listener: ReadyToTransformListener,
) {

    @PostMapping("/work-sleep")
    fun setWorkSleep(@RequestParam delay: Long) {
        Globals.WORK_SLEEP = delay
    }

    @GetMapping("/total")
    fun getTotal(): ResponseEntity<Totals> = ResponseEntity.ok(listener.totals.get())

}

@Service
class ReadyToTransformListener(
    private val template: AmqpTemplate,
) {

    private val logger: Logger = LoggerFactory.getLogger(ReadyToTransformListener::class.java)

    val totals = AtomicReference(Totals(0, 0, emptyList()))

    @RabbitListener(queues = ["ready-to-transform-queue"], concurrency = "3-30")
    fun listener(message: Message) {
        val body = message.body.toString(Charsets.UTF_8)
        val (id, timestamp) = body.split(":").map { it.toLong() }
        logger.info("Received message $body")
        work()
        logger.info("Processed message $body")
        dispatch(body)

        totals.getAndUpdate(object : UnaryOperator<Totals> {
            override fun apply(t: Totals): Totals = Totals(
                emittedCount = t.emittedCount + 1,
                maxEmittedId = max(t.maxEmittedId, id),
                emittedIds = t.emittedIds + id,
            )
        })
    }

    private fun work() {
        Thread.sleep(Globals.WORK_SLEEP)
    }

    private fun dispatch(body: String) {
        val now = System.currentTimeMillis()
        val message = "$body:$now"
        template.send(
            AmqpDef.TRANSFORMER_EXCHANGE_FANOUT,
            "",
            Message(message.toByteArray()),
        )
        logger.info("Sent message $message")
    }

}

fun main(args: Array<String>) {
    runApplication<TransformerApp>(*args)
}