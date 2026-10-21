package com.meowpay.account

import com.meowpay.api.CurrentCat
import com.meowpay.cat.Cat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class AccountController(private val accounts: AccountRepository) {
    data class Me(val id: Long, val name: String, val balance: Long)

    @GetMapping("/me")
    fun me(@CurrentCat cat: Cat): Me = Me(cat.id!!, cat.name, accounts.balanceByCatId(cat.id!!) ?: 0)
}
