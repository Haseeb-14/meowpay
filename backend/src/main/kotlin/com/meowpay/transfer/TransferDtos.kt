package com.meowpay.transfer

import com.meowpay.cat.CatRef
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

/** Request body for POST /api/transfers. Nullable so validation can report a missing field by name. */
data class CreateTransferRequest(
    @field:NotNull val toCatId: Long?,
    @field:NotNull @field:Min(1) @field:Max(1_000_000_000) val amount: Long?,
    @field:Size(max = 140) val note: String?,
)

/** What the service needs to move treats. The sender is resolved from the request's identity. */
data class TransferCommand(
    val fromCatId: Long,
    val toCatId: Long,
    val amount: Long,
    val note: String?,
    val idempotencyKey: String,
)

data class TransferView(
    val id: UUID,
    val from: CatRef,
    val to: CatRef,
    val amount: Long,
    val note: String?,
    val createdAt: Instant,
)

/** `replayed` is true when the Idempotency-Key matched an earlier transfer and nothing new happened. */
data class TransferResult(val transfer: TransferView, val replayed: Boolean)
