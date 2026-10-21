package com.meowpay.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("meowpay")
data class MeowPayProperties(
    val lockTimeoutMs: Long = 3000,

    val corsAllowedOrigins: List<String> = listOf("http://localhost:3000"),
)
