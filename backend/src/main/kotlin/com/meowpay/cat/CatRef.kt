package com.meowpay.cat

data class CatRef(val id: Long, val name: String)

fun Cat.toRef() = CatRef(id!!, name)
