package com.meowpay.api

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * Every error leaves the API in the same shape:
 * `{ "status": 422, "title": "insufficient_funds", "detail": "...", "code": "insufficient_funds" }`
 */
@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(ApiException::class)
    fun api(e: ApiException): ProblemDetail = problem(e.status, e.code, e.message)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun validation(e: MethodArgumentNotValidException): ProblemDetail {
        val first = e.bindingResult.fieldErrors.firstOrNull()
        val message = if (first != null) "${first.field}: ${first.defaultMessage}" else "Invalid request"
        return problem(HttpStatus.BAD_REQUEST, "validation_error", message)
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun unreadable(e: HttpMessageNotReadableException): ProblemDetail =
        problem(HttpStatus.BAD_REQUEST, "malformed_body", "Request body is not valid JSON for this endpoint")

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun missingHeader(e: MissingRequestHeaderException): ProblemDetail =
        problem(HttpStatus.BAD_REQUEST, "missing_header", "Missing required header ${e.headerName}")

    private fun problem(status: HttpStatus, code: String, message: String): ProblemDetail =
        ProblemDetail.forStatusAndDetail(status, message).apply {
            title = code
            setProperty("code", code)
        }
}
