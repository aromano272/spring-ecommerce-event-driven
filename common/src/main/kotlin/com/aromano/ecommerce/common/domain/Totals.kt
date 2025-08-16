package com.aromano.ecommerce.common.domain

data class Totals(
    val emittedCount: Int,
    val maxEmittedId: Long,
    val emittedIds: List<Long>,
)

