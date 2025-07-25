package com.aromano.ecommerce.order

import com.aromano.ecommerce.common.Cents
import com.aromano.ecommerce.order.domain.Product
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.kafka.clients.admin.internals.AdminApiHandler.ApiResult.completed
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Component
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
class CreateOrderSaga(
    private val kafkaTemplate: KafkaTemplate<String, Any>,
    private val orderService: OrderService,
    private val objectMapper: ObjectMapper,
) : Saga<CreateOrderSagaArgs, CreateOrderSagaResult> {

    private val logger = LoggerFactory.getLogger(CreateOrderSaga::class.java)

    private enum class Step {
        CREATE_ORDER,
        DECREMENT_INVENTORY,
        DECREMENT_BALANCE,
        COMPLETE_ORDER_CREATION,
    }

    override val uuid: UUID = UUID.randomUUID()

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
        this.userId = args.userId
        this.products = args.products
        run()
        return onComplete
    }

    private fun run() {
        if (completed) return
        logger.info("run() currStep: $currStep")
        when (currStep) {
            Step.CREATE_ORDER -> {
                orderId = orderService.createOrder(userId, products)
                currStepIdx++
                run()
            }

            Step.DECREMENT_INVENTORY -> kafkaTemplate.send(
                "inventory-commands",
                orderId.toString(),
                DecrementInventoryCommand(
                    orderId = orderId,
                    userId = userId,
                    productIds = products.map { it.id },
                )
            )

            Step.DECREMENT_BALANCE -> kafkaTemplate.send(
                "customer-commands",
                orderId.toString(),
                DecrementBalanceCommand(
                    orderId = orderId,
                    userId = userId,
                    amount = products.sumOf { it.price },
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
        if (completed) return
        when (currStep) {
            Step.CREATE_ORDER -> orderService.rejectOrder(orderId)
            Step.DECREMENT_INVENTORY -> kafkaTemplate.send(
                "inventory-commands",
                orderId.toString(),
                RollbackDecrementInventoryCommand(
                    orderId = orderId,
                    userId = userId,
                    productIds = products.map { it.id },
                )
            )
            Step.DECREMENT_BALANCE -> kafkaTemplate.send(
                "customer-commands",
                orderId.toString(),
                RollbackDecrementBalanceCommand(
                    orderId = orderId,
                    userId = userId,
                    amount = products.sumOf { it.price },
                )
            )
            Step.COMPLETE_ORDER_CREATION -> {}
        }
        if (currStep == Step.entries.first()) {
            completed = true
            onComplete.completeExceptionally(RuntimeException(error))
        }
        currStepIdx--
        rollback()
    }

    @KafkaListener(topics = ["inventory-events"], groupId = "create-order-saga")
    fun handleInventoryEvents(@Payload payload: String) {
        if (currStep != Step.DECREMENT_INVENTORY) return
        logger.info("handleInventoryEvents payload: $payload")
        val event: KafkaEvent = objectMapper.toValue(
            payload,
            InventoryDecrementSuccess::class,
            InventoryDecrementFailed::class,
        ) ?: return

        when (event) {
            is InventoryDecrementSuccess -> if (event.orderId != orderId) return
            is InventoryDecrementFailed -> if (event.orderId != orderId) {
                return
            } else {
                error = "InventoryDecrementFailed"
                rollback()
                return
            }
        }

        currStepIdx++
        run()
    }

    @KafkaListener(topics = ["customer-events"], groupId = "create-order-saga")
    fun handleCustomerEvents(@Payload payload: String) {
        if (currStep != Step.DECREMENT_BALANCE) return
        logger.info("handleCustomerEvents payload: $payload")
        val event: KafkaEvent = objectMapper.toValue(
            payload,
            BalanceDecrementSuccess::class,
            BalanceDecrementFailed::class,
        ) ?: return

        when (event) {
            is BalanceDecrementSuccess -> if (event.orderId != orderId) return
            is BalanceDecrementFailed -> if (event.orderId != orderId) {
                return
            } else {
                error = "BalanceDecrementFailed"
                rollback()
                return
            }
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
    val eventType: String = this::class.simpleName.orEmpty()
}

data class DecrementInventoryCommand(
    val orderId: Int,
    val userId: Int,
    val productIds: List<Int>,
) : KafkaEvent()

data class RollbackDecrementInventoryCommand(
    val orderId: Int,
    val userId: Int,
    val productIds: List<Int>,
) : KafkaEvent()

data class InventoryDecrementSuccess(
    val orderId: Int,
) : KafkaEvent()

data class InventoryDecrementFailed(
    val orderId: Int,
    val error: String,
) : KafkaEvent()

data class DecrementBalanceCommand(
    val orderId: Int,
    val userId: Int,
    val amount: Cents,
) : KafkaEvent()

data class RollbackDecrementBalanceCommand(
    val orderId: Int,
    val userId: Int,
    val amount: Cents,
) : KafkaEvent()

data class BalanceDecrementSuccess(
    val orderId: Int,
) : KafkaEvent()

data class BalanceDecrementFailed(
    val orderId: Int,
    val error: String,
) : KafkaEvent()

