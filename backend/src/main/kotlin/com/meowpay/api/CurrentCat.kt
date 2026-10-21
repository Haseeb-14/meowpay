package com.meowpay.api

import com.meowpay.cat.Cat
import com.meowpay.cat.CatRepository
import org.springframework.core.MethodParameter
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class CurrentCat

@Component
class CurrentCatArgumentResolver(private val cats: CatRepository) : HandlerMethodArgumentResolver {
    override fun supportsParameter(parameter: MethodParameter): Boolean =
        parameter.hasParameterAnnotation(CurrentCat::class.java) &&
            parameter.parameterType == Cat::class.java

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): Any {
        val raw = webRequest.getHeader(HEADER)
            ?: throw ApiException.unauthenticated("Missing $HEADER header")
        val id = raw.trim().toLongOrNull()
            ?: throw ApiException.unauthenticated("$HEADER must be a cat id")
        return cats.findByIdOrNull(id)
            ?: throw ApiException.unauthenticated("Unknown cat $id")
    }

    companion object {
        const val HEADER = "X-Cat-Id"
    }
}
