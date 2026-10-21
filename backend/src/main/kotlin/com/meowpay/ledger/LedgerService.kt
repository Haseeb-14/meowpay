package com.meowpay.ledger

import com.meowpay.cat.CatRef
import com.meowpay.cat.toRef
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

data class LedgerLine(
    val id: Long,
    val kind: LedgerKind,
    val amount: Long,
    val balanceAfter: Long,
    val counterparty: CatRef?,
    val note: String?,
    val transferId: UUID?,
    val createdAt: Instant,
)

@Service
class LedgerService(private val ledger: LedgerEntryRepository) {
    @Transactional(readOnly = true)
    fun statement(catId: Long, limit: Int = 50): List<LedgerLine> =
        ledger.statement(catId, PageRequest.of(0, limit)).map { e ->
            val t = e.transfer
            val counterparty = when (e.kind) {
                LedgerKind.TRANSFER_OUT -> t?.toAccount?.cat?.toRef()
                LedgerKind.TRANSFER_IN -> t?.fromAccount?.cat?.toRef()
                LedgerKind.TOPUP -> null
            }
            LedgerLine(e.id!!, e.kind, e.amount, e.balanceAfter, counterparty, t?.note, t?.id, e.createdAt)
        }
}
