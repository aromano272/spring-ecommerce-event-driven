package com.aromano.ecommerce.common.domain

data class Totals(
    val emittedCount: Int = 0,
    val maxEmittedId: Long = 0,
//    val emittedIds: List<Long> = emptyList(),
    val emissionsPerSec: Int = 0,
)
