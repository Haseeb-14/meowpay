package com.meowpay.api

import com.meowpay.config.MeowPayProperties
import org.springframework.context.annotation.Configuration
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebConfig(
    private val currentCat: CurrentCatArgumentResolver,
    private val props: MeowPayProperties,
) : WebMvcConfigurer {
    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        resolvers.add(currentCat)
    }

    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/api/**")
            .allowedOrigins(*props.corsAllowedOrigins.toTypedArray())
            .allowedMethods("GET", "POST", "OPTIONS")
            .allowedHeaders("Content-Type", "X-Cat-Id", "Idempotency-Key")
            .exposedHeaders("Idempotency-Replayed")
    }
}
