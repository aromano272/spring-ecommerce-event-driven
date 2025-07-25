package com.aromano.ecommerce.order

import org.springframework.stereotype.Component
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

enum class SagaState {
    CREATED,
    RUNNING,
    ROLLING_BACK,
    COMPLETED_SUCCESS,
    COMPLETED_FAILED,
}

interface Saga<Args, Result> {
    val uuid: UUID
    fun start(args: Args): CompletableFuture<Result>
}

@Component
class SagaRunner {

    private val mem = ConcurrentHashMap<UUID, Saga<*, *>>()

    fun <Args, Result> start(saga: Saga<Args, Result>, args: Args): CompletableFuture<Result> {
        mem.put(saga.uuid, saga)
        return saga.start(args).whenComplete { _, _ ->
            mem.remove(saga.uuid)
        }
    }

}