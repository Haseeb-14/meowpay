package com.meowpay.transfer

import com.meowpay.account.AccountRepository
import com.meowpay.api.ApiException
import com.meowpay.cat.Cat
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

data class TransferCommand(
    val fromCatId: Long,
    val toCatId: Long,
    val amount: Long,
    val note: String?,
    val idempotencyKey: String,
)

data class CatRef(val id: Long, val name: String)

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

@Service
class TransferService(
    private val accounts: AccountRepository,
    private val transfers: TransferRepository,
    private val ledger: LedgerEntryRepository,
) {

    /**
     * Move treats between two cats, atomically.
     *
     * Both account rows are locked with SELECT ... FOR UPDATE in ascending cat-id order:
     *  - the fixed order means A->B and B->A can never deadlock each other;
     *  - the sender's lock serialises every transfer from that cat, so the
     *    idempotency lookup below cannot race with itself and the balance check
     *    always sees the latest committed value.
     * Any exception rolls the whole thing back; nothing is ever half-applied.
     */
    @Transactional
    fun transfer(cmd: TransferCommand): TransferResult {
        require(cmd.amount > 0) { "amount must be positive" }
        if (cmd.fromCatId == cmd.toCatId) throw ApiException.selfTransfer()

        val locked = listOf(cmd.fromCatId, cmd.toCatId).sorted().associateWith { catId ->
            accounts.lockByCatId(catId) ?: throw ApiException.catNotFound(catId)
        }
        val from = locked.getValue(cmd.fromCatId)
        val to = locked.getValue(cmd.toCatId)

        transfers.findByIdempotencyKey(from.id!!, cmd.idempotencyKey)?.let { existing ->
            val samePayload = existing.toAccount.id == to.id &&
                existing.amount == cmd.amount &&
                existing.note == cmd.note
            if (!samePayload) throw ApiException.idempotencyKeyMismatch()
            return TransferResult(existing.toView(), replayed = true)
        }

        if (from.balance < cmd.amount) throw ApiException.insufficientFunds(from.balance, cmd.amount)

        from.balance -= cmd.amount
        to.balance += cmd.amount

        val transfer = transfers.save(
            Transfer(
                idempotencyKey = cmd.idempotencyKey,
                fromAccount = from,
                toAccount = to,
                amount = cmd.amount,
                note = cmd.note,
            ),
        )
        ledger.saveAll(
            listOf(
                LedgerEntry(from, transfer, LedgerKind.TRANSFER_OUT, -cmd.amount, from.balance),
                LedgerEntry(to, transfer, LedgerKind.TRANSFER_IN, cmd.amount, to.balance),
            ),
        )
        return TransferResult(transfer.toView(), replayed = false)
    }

    @Transactional(readOnly = true)
    fun history(catId: Long, limit: Int = 50): List<TransferView> =
        transfers.history(catId, PageRequest.of(0, limit)).map { it.toView() }

    private fun Transfer.toView() =
        TransferView(id!!, fromAccount.cat.toRef(), toAccount.cat.toRef(), amount, note, createdAt)

    private fun Cat.toRef() = CatRef(id!!, name)
}
