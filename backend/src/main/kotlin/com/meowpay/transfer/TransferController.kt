package com.meowpay.transfer

import com.meowpay.api.ApiException
import com.meowpay.api.CurrentCat
import com.meowpay.cat.Cat
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class TransferController(private val service: TransferService) {
    @PostMapping("/transfers")
    fun create(
        @CurrentCat cat: Cat,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @Valid @RequestBody body: CreateTransferRequest,
    ): ResponseEntity<TransferView> {
        val key = idempotencyKey.trim()
        if (key.isEmpty() || key.length > 200) {
            throw ApiException(HttpStatus.BAD_REQUEST, "invalid_header", "Idempotency-Key must be 1-200 characters")
        }
        val result = service.transfer(
            TransferCommand(
                fromCatId = cat.id!!,
                toCatId = body.toCatId!!,
                amount = body.amount!!,
                note = body.note?.trim()?.ifEmpty { null },
                idempotencyKey = key,
            ),
        )
        return if (result.replayed) {
            ResponseEntity.ok().header("Idempotency-Replayed", "true").body(result.transfer)
        } else {
            ResponseEntity.status(HttpStatus.CREATED).body(result.transfer)
        }
    }

    @GetMapping("/transfers")
    fun list(@CurrentCat cat: Cat): List<TransferView> = service.history(cat.id!!)
}
