package com.meowpay.api

import com.meowpay.cat.CatRepository
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class HealthController(private val cats: CatRepository) {

    /** Liveness plus a round trip to the database. */
    @GetMapping("/api/health")
    fun health(): Map<String, Any> = mapOf("ok" to true, "cats" to cats.count())
}
