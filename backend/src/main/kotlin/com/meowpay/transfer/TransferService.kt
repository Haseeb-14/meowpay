package com.meowpay.transfer

import com.meowpay.account.AccountRepository
import com.meowpay.api.ApiException
import com.meowpay.cat.toRef
import com.meowpay.config.MeowPayProperties
import com.meowpay.ledger.LedgerEntry
import com.meowpay.ledger.LedgerEntryRepository
import com.meowpay.ledger.LedgerKind
import jakarta.persistence.EntityManager
import jakarta.persistence.LockTimeoutException
import jakarta.persistence.PessimisticLockException
import org.springframework.dao.PessimisticLockingFailureException
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class TransferService(
    private val accounts: AccountRepository,
    private val transfers: TransferRepository,
    private val ledger: LedgerEntryRepository,
    private val entityManager: EntityManager,
    private val props: MeowPayProperties,
) {
    @Transactional
    fun transfer(cmd: TransferCommand): TransferResult {
        require(cmd.amount > 0) { "amount must be positive" }
        if (cmd.fromCatId == cmd.toCatId) throw ApiException.selfTransfer()

        entityManager.createNativeQuery("set local lock_timeout = ${props.lockTimeoutMs}").executeUpdate()
        val locked = try {
            listOf(cmd.fromCatId, cmd.toCatId).sorted().associateWith { catId ->
                accounts.lockByCatId(catId) ?: throw ApiException.catNotFound(catId)
            }
        } catch (e: PessimisticLockingFailureException) {
            throw ApiException.tryAgain()
        } catch (e: LockTimeoutException) {
            throw ApiException.tryAgain()
        } catch (e: PessimisticLockException) {
            throw ApiException.tryAgain()
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
}
