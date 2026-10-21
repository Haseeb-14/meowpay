package com.meowpay.api

import org.springframework.http.HttpStatus

/** An error the client can act on. Rendered as RFC 9457 problem+json with a stable `code`. */
class ApiException(val status: HttpStatus, val code: String, override val message: String) : RuntimeException(message) {
    companion object {
        fun unauthenticated(message: String) =
            ApiException(HttpStatus.UNAUTHORIZED, "unauthenticated", message)

        fun catNotFound(catId: Long) =
            ApiException(HttpStatus.NOT_FOUND, "cat_not_found", "No cat with id $catId")

        fun selfTransfer() =
            ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "self_transfer", "A cat cannot send treats to itself")

        fun insufficientFunds(balance: Long, amount: Long) =
            ApiException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "insufficient_funds",
                "Balance is $balance treats, cannot send $amount",
            )

        fun idempotencyKeyMismatch() =
            ApiException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "idempotency_key_mismatch",
                "This Idempotency-Key was already used for a different transfer",
            )
    }
}
