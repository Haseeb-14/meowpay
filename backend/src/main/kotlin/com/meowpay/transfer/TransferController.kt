package com.meowpay.transfer

import com.meowpay.api.ApiException
import com.meowpay.api.CurrentCat
import com.meowpay.cat.Cat
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
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

    data class CreateTransferRequest(
        @field:NotNull val toCatId: Long?,
        @field:NotNull @field:Min(1) @field:Max(1_000_000_000) val amount: Long?,
        @field:Size(max = 140) val note: String?,
    )

    /**
     * Send treats. `Idempotency-Key` is required: retrying with the same key returns the
     * original transfer with 200 instead of moving treats twice. 201 on a new transfer.
     */
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
        val status = if (result.replayed) HttpStatus.OK else HttpStatus.CREATED
        return ResponseEntity.status(status).body(result.transfer)
    }

    /** The acting cat's sent and received transfers, newest first. */
    @GetMapping("/transfers")
    fun list(@CurrentCat cat: Cat): List<TransferView> = service.history(cat.id!!)
}
