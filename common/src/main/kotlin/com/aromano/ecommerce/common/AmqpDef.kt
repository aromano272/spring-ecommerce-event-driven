package com.aromano.ecommerce.common

object AmqpDef {

    @JvmStatic val INGESTER_EXCHANGE_FANOUT = "ingester-exchange-fanout"
    @JvmStatic val TRANSFORMER_EXCHANGE_FANOUT = "transformer-exchange-fanout"

    @JvmStatic val READY_TO_TRANSFORM_QUEUE = "ready-to-transform-queue"
    @JvmStatic val READY_TO_DISPATCH_QUEUE = "ready-to-dispatch-queue"

}