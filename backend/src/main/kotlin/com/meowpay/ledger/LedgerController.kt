package com.meowpay.ledger

import com.meowpay.api.CurrentCat
import com.meowpay.cat.Cat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class LedgerController(private val service: LedgerService) {

    /** The acting cat's ledger: every movement with the balance after it, newest first. */
    @GetMapping("/ledger")
    fun ledger(@CurrentCat cat: Cat): List<LedgerLine> = service.statement(cat.id!!)
}
