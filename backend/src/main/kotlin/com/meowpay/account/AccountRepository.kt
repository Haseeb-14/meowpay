package com.meowpay.account

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface AccountRepository : JpaRepository<Account, Long> {

    /** Loads the account with a row lock held until the surrounding transaction ends. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.cat.id = :catId")
    fun lockByCatId(@Param("catId") catId: Long): Account?

    @Query("select a from Account a where a.cat.id = :catId")
    fun findByCatId(@Param("catId") catId: Long): Account?

    @Query("select a.balance from Account a where a.cat.id = :catId")
    fun balanceByCatId(@Param("catId") catId: Long): Long?
}
