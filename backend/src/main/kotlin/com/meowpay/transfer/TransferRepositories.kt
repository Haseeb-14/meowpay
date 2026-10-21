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

interface LedgerEntryRepository : JpaRepository<LedgerEntry, Long> {

    @Query("select coalesce(sum(e.amount), 0) from LedgerEntry e where e.account.id = :accountId")
    fun sumByAccountId(@Param("accountId") accountId: Long): Long

    @Query("select e from LedgerEntry e where e.transfer.id = :transferId order by e.id")
    fun findByTransferId(@Param("transferId") transferId: UUID): List<LedgerEntry>

    /** The cat's statement, newest first, with the counterparty cats loaded in one query. */
    @Query(
        """
        select e from LedgerEntry e
          left join fetch e.transfer t
          left join fetch t.fromAccount fa left join fetch fa.cat
          left join fetch t.toAccount ta left join fetch ta.cat
        where e.account.cat.id = :catId
        order by e.id desc
        """,
    )
    fun statement(@Param("catId") catId: Long, pageable: Pageable): List<LedgerEntry>
}
