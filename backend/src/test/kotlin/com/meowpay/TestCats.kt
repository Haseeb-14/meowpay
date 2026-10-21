package com.meowpay

import com.meowpay.account.Account
import com.meowpay.account.AccountRepository
import com.meowpay.cat.Cat
import com.meowpay.cat.CatRepository
import com.meowpay.ledger.LedgerEntry
import com.meowpay.ledger.LedgerEntryRepository
import com.meowpay.ledger.LedgerKind
import org.springframework.stereotype.Component
import java.util.UUID

/** Creates fresh cats per test so tests never depend on, or disturb, the seed data. */
@Component
class TestCats(
    private val cats: CatRepository,
    private val accounts: AccountRepository,
    private val ledger: LedgerEntryRepository,
) {
    fun newCat(balance: Long): Cat {
        val cat = cats.save(Cat(name = "test-${UUID.randomUUID()}"))
        val account = accounts.save(Account(cat = cat, balance = balance))
        if (balance > 0) ledger.save(LedgerEntry(account, null, LedgerKind.TOPUP, balance, balance))
        return cat
    }

    fun balance(cat: Cat): Long = accounts.balanceByCatId(cat.id!!)!!

    fun ledgerSum(cat: Cat): Long = ledger.sumByAccountId(accounts.findByCatId(cat.id!!)!!.id!!)
}
