package com.aromano.ecommerce.admindashboard.events

abstract class KafkaEvent {
    abstract val sagaId: String
    val eventType: String = this::class.simpleName.orEmpty()
}

data class InventoryDecrementSuccess(
    override val sagaId: String,
    val orderId: Int
) : KafkaEvent()

data class DecrementIntentoryFailed(
    override val sagaId: String,
    val orderId: Int,
    val error: String
) : KafkaEvent()

data class BalanceDecrementSuccess(
    override val sagaId: String,
    val orderId: Int
) : KafkaEvent()

data class BalanceDecrementFailed(
    override val sagaId: String,
    val orderId: Int,
    val error: String
) : KafkaEvent()
