package com.meowpay.cat

/** The public shape of a cat wherever one is referenced by another resource. */
data class CatRef(val id: Long, val name: String)

fun Cat.toRef() = CatRef(id!!, name)
