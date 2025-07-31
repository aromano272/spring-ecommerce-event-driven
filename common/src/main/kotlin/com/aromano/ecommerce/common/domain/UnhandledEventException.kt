package com.aromano.ecommerce.common.domain

class UnhandledEventException(val eventType: String) : RuntimeException()