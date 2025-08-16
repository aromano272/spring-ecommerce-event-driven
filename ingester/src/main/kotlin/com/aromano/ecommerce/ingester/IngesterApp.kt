package com.aromano.ecommerce.ingester

import com.aromano.ecommerce.common.AmqpDef
import com.aromano.ecommerce.common.config.BaseAmqpConfig
import com.aromano.ecommerce.common.domain.Totals
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.amqp.core.AmqpTemplate
import org.springframework.amqp.core.FanoutExchange
import org.springframework.amqp.core.Message
import org.springframework.amqp.core.Queue
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
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.function.UnaryOperator
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
    private val service: IngesterService,
) {

    @PostMapping("/start")
    fun start(@RequestParam delay: Int) {
        service.start(delay)
    }

    @PostMapping("/stop")
    fun stop() {
        service.stop()
    }

    @GetMapping("/total")
    fun getTotal(): ResponseEntity<Totals> = ResponseEntity.ok(service.totals.get())

}

@Service
class IngesterService(
    private val template: AmqpTemplate,
) {

    private val logger: Logger = LoggerFactory.getLogger(IngesterService::class.java)

    val totals = AtomicReference(Totals(0, 0, emptyList()))

    private var currentTask: ScheduledFuture<*>? =  null
    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private val count = AtomicInteger(0)

    @Synchronized
    fun start(delay: Int) {
        currentTask?.cancel(true)

        currentTask = scheduler.scheduleWithFixedDelay(::emit, 0, delay.toLong(), TimeUnit.MILLISECONDS)
    }

    @Synchronized
    fun stop() {
        currentTask?.cancel(true)
        currentTask = null
    }

    private fun emit() {
        val id = count.andIncrement
        val now = System.currentTimeMillis()
        val message = "$id:$now"
        logger.info("Sending message $message")
        template.send(
            AmqpDef.INGESTER_EXCHANGE_FANOUT,
            "",
            Message(message.toByteArray()),
        )

        totals.getAndUpdate(object : UnaryOperator<Totals> {
            override fun apply(t: Totals): Totals = Totals(
                emittedCount = t.emittedCount + 1,
                maxEmittedId = max(t.maxEmittedId, id.toLong()),
                emittedIds = t.emittedIds + id.toLong(),
            )
        })

        logger.info("Sent message $message")
    }

}

fun main(args: Array<String>) {
    runApplication<IngesterApp>(*args)
}