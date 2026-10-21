package com.meowpay.api

import com.meowpay.cat.CatRepository
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class HealthController(private val cats: CatRepository) {
    @GetMapping("/api/health")
    fun health(): Map<String, Any> = mapOf("ok" to true, "cats" to cats.count())
}
