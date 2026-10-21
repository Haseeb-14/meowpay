package com.meowpay.cat

import com.meowpay.account.AccountRepository
import com.meowpay.api.CurrentCat
import org.springframework.data.domain.Sort
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class CatController(private val cats: CatRepository, private val accounts: AccountRepository) {

    data class CatSummary(val id: Long, val name: String)
    data class Me(val id: Long, val name: String, val balance: Long)

    /** Public directory of cats, used by the UI to pick who you are and who to send to. */
    @GetMapping("/cats")
    fun list(): List<CatSummary> =
        cats.findAll(Sort.by("name")).map { CatSummary(it.id!!, it.name) }

    /** The acting cat and its balance. */
    @GetMapping("/me")
    fun me(@CurrentCat cat: Cat): Me =
        Me(cat.id!!, cat.name, accounts.balanceByCatId(cat.id!!) ?: 0)

    @GetMapping("/health")
    fun health(): Map<String, Any> = mapOf("ok" to true, "cats" to cats.count())
}
