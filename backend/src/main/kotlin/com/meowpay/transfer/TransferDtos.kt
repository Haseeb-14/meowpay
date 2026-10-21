package com.meowpay.transfer

import com.meowpay.cat.CatRef
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class CreateTransferRequest(
    @field:NotNull val toCatId: Long?,
    @field:NotNull @field:Min(1) @field:Max(1_000_000_000) val amount: Long?,
    @field:Size(max = 140) val note: String?,
)

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

data class TransferResult(val transfer: TransferView, val replayed: Boolean)
