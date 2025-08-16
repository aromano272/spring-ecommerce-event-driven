package com.aromano.ecommerce.admindashboard.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestTemplate


@Configuration
class AppConfig {

    @Bean
    fun restTemplate(): RestTemplate {
        val timeout = 20L

        val factory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(timeout.toInt())
            setReadTimeout(timeout.toInt())
        }

        return RestTemplate(factory)
    }
}