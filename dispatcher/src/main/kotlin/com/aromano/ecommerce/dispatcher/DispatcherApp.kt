package com.aromano.ecommerce.dispatcher

import com.aromano.ecommerce.common.AmqpDef
import com.aromano.ecommerce.common.KafkaRef
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
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service

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

    @RabbitListener(queues = ["ready-to-dispatch-queue"])
    // TODO(aromano): test with suspend
    fun listener(message: Message) {
        val body = message.body.toString(Charsets.UTF_8)
        val (id, ingestedAt, transformedAt) = body.split(":").map { it.toLong() }

        logger.info("Received message $body")

        template.send(KafkaRef.TOPIC_DISPATCH, id.toString(), body)

        logger.info("Sent message $body")
    }

}

fun main(args: Array<String>) {
    runApplication<DispatcherApp>(*args)
}