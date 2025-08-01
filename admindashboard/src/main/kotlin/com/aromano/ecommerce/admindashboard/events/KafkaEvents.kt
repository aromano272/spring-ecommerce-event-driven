package com.aromano.ecommerce.admindashboard.events

abstract class KafkaEvent {
    abstract val sagaId: String
    val eventType: String = this::class.simpleName.orEmpty()
}

data class ReserveInventorySuccess(
    override val sagaId: String,
    val orderId: Int,
    val totalCost: Int
) : KafkaEvent()

data class ReserveInventoryFailed(
    override val sagaId: String,
    val orderId: Int,
    val error: String
) : KafkaEvent()

data class ReserveBalanceSuccess(
    override val sagaId: String,
    val orderId: Int
) : KafkaEvent()

data class ReserveBalanceFailed(
    override val sagaId: String,
    val orderId: Int,
    val error: String
) : KafkaEvent()

data class SubmitReservedBalanceSuccess(
    override val sagaId: String,
    val orderId: Int
) : KafkaEvent()

data class SubmitReservedBalanceFailed(
    override val sagaId: String,
    val orderId: Int,
    val error: String
) : KafkaEvent()

data class ReleasedReservedBalanceSuccess(
    override val sagaId: String,
    val orderId: Int
) : KafkaEvent()

data class ReleasedReservedBalanceFailed(
    override val sagaId: String,
    val orderId: Int,
    val error: String
) : KafkaEvent()
