package com.meowpay.ledger

import com.meowpay.api.CurrentCat
import com.meowpay.cat.Cat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class LedgerController(private val service: LedgerService) {
    @GetMapping("/ledger")
    fun ledger(@CurrentCat cat: Cat): List<LedgerLine> = service.statement(cat.id!!)
}
