package com.aromano.ecommerce.order

import com.aromano.ecommerce.common.Cents
import com.aromano.ecommerce.common.domain.UnhandledEventException
import com.aromano.ecommerce.order.domain.Product
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.ProducerListener
import org.springframework.stereotype.Component
import java.lang.Exception
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.jvm.java
import kotlin.properties.Delegates
import kotlin.reflect.KClass

interface CustomerServiceProxy {

}

interface InventoryServiceProxy {

}

data class CreateOrderSagaArgs(
    val userId: Int,
    val products: List<Product>,
)

data class CreateOrderSagaResult(
    val orderId: Int,
)

@Component
class CreateOrderSagaFactory(
    private val kafkaTemplate: KafkaTemplate<String, Any>,
    private val orderService: OrderService,
    private val objectMapper: ObjectMapper,
) {
    fun create(): CreateOrderSaga = CreateOrderSaga(kafkaTemplate, orderService, objectMapper)
}

class CreateOrderSaga(
    private val kafkaTemplate: KafkaTemplate<String, Any>,
    private val orderService: OrderService,
    private val objectMapper: ObjectMapper,
) : Saga<CreateOrderSagaArgs, CreateOrderSagaResult> {

    private val logger = LoggerFactory.getLogger(CreateOrderSaga::class.java)

    init {
        kafkaTemplate.setProducerListener(object : ProducerListener<String, Any> {
            override fun onSuccess(
                producerRecord: ProducerRecord<String?, in Any>?,
                recordMetadata: RecordMetadata?
            ) {
                logger.info("Event success: key: ${producerRecord?.key()}, value: ${producerRecord?.value()}")
            }

            override fun onError(
                producerRecord: ProducerRecord<String?, in Any>?,
                recordMetadata: RecordMetadata?,
                exception: Exception?
            ) {
                logger.info("Event success: key: ${producerRecord?.key()}, value: ${producerRecord?.value()}, ex: $exception")
            }
        })
    }

    private enum class Step {
        CREATE_ORDER,
        RESERVE_INVENTORY,
        DECREMENT_BALANCE,
        SUBMIT_RESERVED_INVENTORY,
        COMPLETE_ORDER_CREATION,
    }

    override val uuid: String = UUID.randomUUID().toString()

    private var userId by Delegates.notNull<Int>()
    private lateinit var products: List<Product>
    private var orderId by Delegates.notNull<Int>()
    private var completed = false
    private lateinit var error: String

    private var currStepIdx = 0
    private val currStep: Step get() = Step.entries[currStepIdx]

    private val onComplete = CompletableFuture<CreateOrderSagaResult>()

    // TODO(aromano): still todo:
    // * store saga state on disk
    // * saga should either run async or return it's result to the caller(maybe futures?)
    // * build the sagarunner that's responsible for removing completed sagas off memory
    //      so it doesn't keep receiving events, also it should restore sagas from disk
    //      when the service restarts for whatever reason(look into how kafka supports this
    //      late start, does it replay missed events?)

    override fun start(args: CreateOrderSagaArgs): CompletableFuture<CreateOrderSagaResult> {
        logger.info("start UUID: $uuid")
        this.userId = args.userId
        this.products = args.products
        run()
        return onComplete
    }

    private fun run() {
        if (completed) return
        logger.info("$uuid run() currStep: $currStep")
        when (currStep) {
            Step.CREATE_ORDER -> {
                orderId = orderService.createOrder(userId, products)
                logger.info("$uuid run() orderId: $orderId")
                currStepIdx++
                run()
            }

            Step.RESERVE_INVENTORY -> kafkaTemplate.send(
                "inventory-commands",
                orderId.toString(),
                ReserveInventoryCommand(
                    sagaId = uuid,
                    orderId = orderId,
                    userId = userId,
                    product = products,
                )
            )

            Step.DECREMENT_BALANCE -> kafkaTemplate.send(
                "customer-commands",
                orderId.toString(),
                DecrementBalanceCommand(
                    sagaId = uuid,
                    orderId = orderId,
                    userId = userId,
                    amount = products.sumOf { it.price },
                )
            )

            Step.SUBMIT_RESERVED_INVENTORY -> kafkaTemplate.send(
                "inventory-commands",
                orderId.toString(),
                SubmitReservedInventoryCommand(
                    sagaId = uuid,
                    orderId = orderId,
                    userId = userId,
                )
            )

            Step.COMPLETE_ORDER_CREATION -> orderService.completeOrderCreation(orderId)
        }

        if (currStep == Step.entries.last()) {
            completed = true
            onComplete.complete(CreateOrderSagaResult(orderId))
        }
    }

    fun rollback() {
        currStepIdx--
        if (currStepIdx < 0) {
            completed = true
            onComplete.completeExceptionally(RuntimeException(error))
            return
        }
        logger.info("$uuid rollback() currStep: $currStep")
        when (currStep) {
            Step.CREATE_ORDER -> orderService.rejectOrder(orderId)
            Step.RESERVE_INVENTORY -> kafkaTemplate.send(
                "inventory-commands",
                orderId.toString(),
                RollbackReserveInventoryCommand(
                    sagaId = uuid,
                    orderId = orderId,
                    userId = userId,
                    productIds = products.map { it.id },
                )
            )
            Step.DECREMENT_BALANCE -> kafkaTemplate.send(
                "customer-commands",
                orderId.toString(),
                RollbackDecrementBalanceCommand(
                    sagaId = uuid,
                    orderId = orderId,
                    userId = userId,
                    amount = products.sumOf { it.price },
                )
            )
            // TODO(aromano): considering these steps as "unfailable", will need to revisit later
            Step.SUBMIT_RESERVED_INVENTORY,
            Step.COMPLETE_ORDER_CREATION -> {}
        }
        rollback()
    }

    override fun handleEvent(event: KafkaEvent) {
        when (event) {
            is ReserveInventorySuccess -> {
                if (currStep != Step.RESERVE_INVENTORY) return
                if (event.orderId != orderId) return
            }
            is ReserveInventoryFailed -> {
                if (currStep != Step.RESERVE_INVENTORY) return
                if (event.orderId != orderId) return
                error = "ReserveInventoryFailed"
                rollback()
                return
            }
            is BalanceDecrementSuccess -> {
                if (currStep != Step.DECREMENT_BALANCE) return
                if (event.orderId != orderId) return
            }
            is BalanceDecrementFailed -> {
                if (currStep != Step.DECREMENT_BALANCE) return
                if (event.orderId != orderId) return
                error = "BalanceDecrementFailed"
                rollback()
                return
            }
            is SubmitReservedInventorySuccess -> {
                if (currStep != Step.SUBMIT_RESERVED_INVENTORY) return
                if (event.orderId != orderId) return
            }
            is ReleasedReservedInventorySuccess -> return
            is SubmitReservedInventoryFailed,
            is ReleasedReservedInventoryFailed -> throw UnsupportedOperationException()
            else -> throw UnhandledEventException(event.eventType)
        }
        currStepIdx++
        run()
    }

}

inline fun <reified T : KafkaEvent> ObjectMapper.toValue(payload: String): T? {
    val node = readTree(payload)
    val eventType = node["eventType"]?.asText() ?: return null
    val match = T::class.simpleName == eventType
    if (!match) return null
    return treeToValue(node, T::class.java)
}

fun ObjectMapper.toValue(payload: String, vararg events: KClass<*>): KafkaEvent? {
    val node = readTree(payload)
    val eventType = node["eventType"]?.asText() ?: return null
    val match = events.find { event -> event.simpleName == eventType }
    return match?.let {
        treeToValue(node, it.java) as? KafkaEvent
    }
}

abstract class KafkaEvent {
    abstract val sagaId: String
    val eventType: String = this::class.simpleName.orEmpty()
}

data class ReserveInventoryCommand(
    override val sagaId: String,
    val orderId: Int,
    val userId: Int,
    val product: List<Product>,
) : KafkaEvent()

data class DecrementBalanceCommand(
    override val sagaId: String,
    val orderId: Int,
    val userId: Int,
    val amount: Cents,
) : KafkaEvent()

data class SubmitReservedInventoryCommand(
    override val sagaId: String,
    val orderId: Int,
    val userId: Int,
) : KafkaEvent()

data class RollbackReserveInventoryCommand(
    override val sagaId: String,
    val orderId: Int,
    val userId: Int,
    val productIds: List<Int>,
) : KafkaEvent()

data class ReserveInventorySuccess(
    override val sagaId: String,
    val orderId: Int,
    val totalCost: Cents,
) : KafkaEvent()

data class ReserveInventoryFailed(
    override val sagaId: String,
    val orderId: Int,
    val error: String,
) : KafkaEvent()

data class SubmitReservedInventorySuccess(
    override val sagaId: String,
    val orderId: Int,
) : KafkaEvent()

data class SubmitReservedInventoryFailed(
    override val sagaId: String,
    val orderId: Int,
    val error: String,
) : KafkaEvent()

data class ReleasedReservedInventorySuccess(
    override val sagaId: String,
    val orderId: Int,
) : KafkaEvent()

data class ReleasedReservedInventoryFailed(
    override val sagaId: String,
    val orderId: Int,
    val error: String,
) : KafkaEvent()

data class RollbackDecrementBalanceCommand(
    override val sagaId: String,
    val orderId: Int,
    val userId: Int,
    val amount: Cents,
) : KafkaEvent()

data class BalanceDecrementSuccess(
    override val sagaId: String,
    val orderId: Int,
) : KafkaEvent()

data class BalanceDecrementFailed(
    override val sagaId: String,
    val orderId: Int,
    val error: String,
) : KafkaEvent()

