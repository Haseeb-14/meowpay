package com.meowpay.api

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebConfig(
    private val currentCat: CurrentCatArgumentResolver,
    @Value("\${meowpay.cors-allowed-origins}") private val allowedOrigins: List<String>,
) : WebMvcConfigurer {

    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        resolvers.add(currentCat)
    }

    /** The web app goes through its own proxy; this is for calling :8080 straight from a browser. */
    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/api/**")
            .allowedOrigins(*allowedOrigins.toTypedArray())
            .allowedMethods("GET", "POST", "OPTIONS")
            .allowedHeaders("Content-Type", "X-Cat-Id", "Idempotency-Key")
            .exposedHeaders("Idempotency-Replayed")
    }
}
