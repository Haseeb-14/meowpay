package com.meowpay.config

import org.springframework.boot.context.properties.ConfigurationProperties

/** Everything under `meowpay.*` in application.yml, bound once and validated at startup. */
@ConfigurationProperties("meowpay")
data class MeowPayProperties(
    /** How long a transfer may wait for its account rows before failing with 503 try_again. */
    val lockTimeoutMs: Long = 3000,
    /** Browser origins allowed to call the API directly; the Next.js proxy needs none. */
    val corsAllowedOrigins: List<String> = listOf("http://localhost:3000"),
)
