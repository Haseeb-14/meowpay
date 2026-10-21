package com.meowpay.transfer

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface TransferRepository : JpaRepository<Transfer, UUID> {

    @Query("select t from Transfer t where t.fromAccount.id = :accountId and t.idempotencyKey = :key")
    fun findByIdempotencyKey(@Param("accountId") accountId: Long, @Param("key") key: String): Transfer?

    /** Everything the cat sent or received, newest first, with both cats loaded in one query. */
    @Query(
        """
        select t from Transfer t
          join fetch t.fromAccount fa join fetch fa.cat
          join fetch t.toAccount ta join fetch ta.cat
        where fa.cat.id = :catId or ta.cat.id = :catId
        order by t.seq desc
        """,
    )
    fun history(@Param("catId") catId: Long, pageable: Pageable): List<Transfer>
}
