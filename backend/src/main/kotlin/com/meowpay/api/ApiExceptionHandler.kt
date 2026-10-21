package com.meowpay.api

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.ErrorResponse
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

/**
 * Every error leaves the API in the same shape:
 * `{ "status": 422, "title": "insufficient_funds", "detail": "...", "code": "insufficient_funds", "instance": "/api/transfers" }`
 *
 * Specific handlers first; [fallback] catches everything else so no exception can
 * escape with a different body, and nothing internal leaks into a 500.
 */
@RestControllerAdvice
class ApiExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

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

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun typeMismatch(e: MethodArgumentTypeMismatchException): ProblemDetail =
        problem(HttpStatus.BAD_REQUEST, "invalid_parameter", "${e.name} has the wrong type")

    @ExceptionHandler(Exception::class)
    fun fallback(e: Exception): ResponseEntity<ProblemDetail> {
        // Spring's own 404/405/415/406 exceptions carry a ProblemDetail already; keep their
        // status and add our code so clients never see a second error shape.
        if (e is ErrorResponse) {
            val status = e.statusCode
            val body = e.body.apply {
                val code = codeFor(status)
                title = code
                setProperty("code", code)
            }
            return ResponseEntity.status(status).body(body)
        }
        log.error("Unhandled exception", e)
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error", "Something went wrong on our side"))
    }

    private fun codeFor(status: HttpStatusCode): String = when (status.value()) {
        404 -> "not_found"
        405 -> "method_not_allowed"
        406 -> "not_acceptable"
        415 -> "unsupported_media_type"
        else -> if (status.is4xxClientError) "bad_request" else "internal_error"
    }

    private fun problem(status: HttpStatus, code: String, message: String): ProblemDetail =
        ProblemDetail.forStatusAndDetail(status, message).apply {
            title = code
            setProperty("code", code)
        }
}
