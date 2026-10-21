package com.meowpay.cat

import org.springframework.data.domain.Sort
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class CatController(private val cats: CatRepository) {

    /** Public directory of cats, used by the UI to pick who you are and who to send to. */
    @GetMapping("/cats")
    fun list(): List<CatRef> = cats.findAll(Sort.by("name")).map { it.toRef() }
}
