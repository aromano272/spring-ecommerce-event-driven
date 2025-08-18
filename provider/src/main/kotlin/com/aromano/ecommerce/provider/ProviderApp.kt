package com.aromano.ecommerce.provider

import com.aromano.ecommerce.common.AmqpDef
import com.aromano.ecommerce.common.KafkaRef
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
import org.springframework.kafka.core.KafkaTemplate
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
import kotlin.jvm.java
import kotlin.math.max

@SpringBootApplication
class ProviderApp

@RestController
@RequestMapping("/provider")
class ProviderController(
    private val service: ProviderService,
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
    fun getTotal(): ResponseEntity<Totals> = ResponseEntity.ok(service.totals.getAndUpdate { it.copy(emittedCount = 0) })

}

@Service
class ProviderService(
    private val template: KafkaTemplate<String, String>,
) {

    private val logger: Logger = LoggerFactory.getLogger(ProviderService::class.java)

    val totals = AtomicReference(Totals())

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
        val message = "$id"
        logger.info("Sending message $message")
        template.send(KafkaRef.TOPIC_PROVIDER, id.toString(), message)

        totals.getAndUpdate {
            Totals(
                emittedCount = it.emittedCount + 1,
                maxEmittedId = max(it.maxEmittedId, id.toLong()),
//                emittedIds = it.emittedIds + id.toLong(),
            )
        }

        logger.info("Sent message $message")
    }

}

fun main(args: Array<String>) {
    runApplication<ProviderApp>(*args)
}