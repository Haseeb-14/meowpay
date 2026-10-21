package com.meowpay.transfer

import com.meowpay.TestCats
import com.meowpay.TestcontainersConfiguration
import com.meowpay.account.AccountRepository
import com.meowpay.api.ApiException
import com.meowpay.ledger.LedgerEntryRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest
@Import(TestcontainersConfiguration::class)
@ActiveProfiles("test")
class TransferServiceTest @Autowired constructor(
    private val service: TransferService,
    private val ledger: LedgerEntryRepository,
    private val accounts: AccountRepository,
    private val tx: TransactionTemplate,
    private val cats: TestCats,
) {

    private fun send(from: Long, to: Long, amount: Long, key: String = UUID.randomUUID().toString(), note: String? = null) =
        service.transfer(TransferCommand(from, to, amount, note, key))

    @Test
    fun `moves treats and records a balanced ledger`() {
        val a = cats.newCat(100)
        val b = cats.newCat(0)

        val result = send(a.id!!, b.id!!, 30, note = "for the zoomies")

        assertEquals(false, result.replayed)
        assertEquals(70, cats.balance(a))
        assertEquals(30, cats.balance(b))

        val entries = ledger.findByTransferId(result.transfer.id)
        assertEquals(2, entries.size)
        assertEquals(0, entries.sumOf { it.amount })
        assertEquals(listOf(-30L, 30L), entries.map { it.amount })
        assertEquals(listOf(70L, 30L), entries.map { it.balanceAfter })
        assertEquals(cats.balance(a), cats.ledgerSum(a))
        assertEquals(cats.balance(b), cats.ledgerSum(b))
    }

    @Test
    fun `rejects insufficient funds and changes nothing`() {
        val a = cats.newCat(10)
        val b = cats.newCat(0)

        val e = assertThrows<ApiException> { send(a.id!!, b.id!!, 11) }

        assertEquals("insufficient_funds", e.code)
        assertEquals(10, cats.balance(a))
        assertEquals(0, cats.balance(b))
        assertTrue(service.history(a.id!!).isEmpty())
    }

    @Test
    fun `replaying an idempotency key returns the original transfer and debits once`() {
        val a = cats.newCat(100)
        val b = cats.newCat(0)
        val key = "retry-${UUID.randomUUID()}"

        val first = send(a.id!!, b.id!!, 25, key)
        val second = send(a.id!!, b.id!!, 25, key)

        assertEquals(first.transfer.id, second.transfer.id)
        assertEquals(false, first.replayed)
        assertEquals(true, second.replayed)
        assertEquals(75, cats.balance(a))
        assertEquals(1, service.history(a.id!!).size)
    }

    @Test
    fun `reusing an idempotency key with a different payload is rejected`() {
        val a = cats.newCat(100)
        val b = cats.newCat(0)
        val key = "reuse-${UUID.randomUUID()}"

        send(a.id!!, b.id!!, 25, key)
        val e = assertThrows<ApiException> { send(a.id!!, b.id!!, 26, key) }

        assertEquals("idempotency_key_mismatch", e.code)
        assertEquals(75, cats.balance(a))
    }

    @Test
    fun `a cat cannot send treats to itself`() {
        val a = cats.newCat(100)
        val e = assertThrows<ApiException> { send(a.id!!, a.id!!, 1) }
        assertEquals("self_transfer", e.code)
        assertEquals(100, cats.balance(a))
    }

    @Test
    fun `sending to an unknown cat is not found`() {
        val a = cats.newCat(100)
        val e = assertThrows<ApiException> { send(a.id!!, 999_999_999, 1) }
        assertEquals("cat_not_found", e.code)
        assertEquals(100, cats.balance(a))
    }

    @Test
    fun `concurrent sends never overdraw the account`() {
        val a = cats.newCat(100)
        val b = cats.newCat(0)
        val attempts = 20 // 20 x 10 treats against a balance of 100: exactly 10 can win

        val codes = runConcurrently(attempts) { send(a.id!!, b.id!!, 10) }

        assertEquals(10, codes.count { it == "ok" }, "winners: $codes")
        assertEquals(10, codes.count { it == "insufficient_funds" }, "losers: $codes")
        assertEquals(0, cats.balance(a))
        assertEquals(100, cats.balance(b))
        assertEquals(cats.balance(a), cats.ledgerSum(a))
        assertEquals(cats.balance(b), cats.ledgerSum(b))
    }

    @Test
    fun `opposite-direction transfers do not deadlock and conserve treats`() {
        val a = cats.newCat(1000)
        val b = cats.newCat(1000)
        val rounds = 40

        val codes = runConcurrently(2) { worker ->
            repeat(rounds) {
                if (worker % 2 == 0) send(a.id!!, b.id!!, 3) else send(b.id!!, a.id!!, 3)
            }
        }

        assertEquals(listOf("ok", "ok"), codes)
        assertEquals(1000, cats.balance(a))
        assertEquals(1000, cats.balance(b))
        assertEquals(rounds * 2, service.history(a.id!!, limit = 200).size)
    }

    @Test
    fun `gives up with try_again when the sender's row stays locked past the timeout`() {
        val a = cats.newCat(100)
        val b = cats.newCat(0)
        val lockHeld = CountDownLatch(1)
        val release = CountDownLatch(1)
        val holder = Thread {
            tx.execute {
                accounts.lockByCatId(a.id!!)
                lockHeld.countDown()
                release.await(10, TimeUnit.SECONDS)
            }
        }.apply { start() }
        lockHeld.await(10, TimeUnit.SECONDS)

        val e = assertThrows<ApiException> { send(a.id!!, b.id!!, 1) }

        release.countDown()
        holder.join()
        assertEquals("try_again", e.code)
        assertEquals(100, cats.balance(a))
        assertTrue(service.history(a.id!!).isEmpty())
    }

    /** Runs [block] on [n] threads released at the same instant; returns "ok" or the ApiException code per thread. */
    private fun runConcurrently(n: Int, block: (Int) -> Unit): List<String> {
        val pool = Executors.newFixedThreadPool(n)
        val start = CountDownLatch(1)
        val futures = (0 until n).map { i ->
            pool.submit<String> {
                start.await()
                try {
                    block(i); "ok"
                } catch (e: ApiException) {
                    e.code
                }
            }
        }
        start.countDown()
        val codes = futures.map { it.get(60, TimeUnit.SECONDS) }
        pool.shutdown()
        return codes
    }
}
