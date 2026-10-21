package com.meowpay.ledger

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface LedgerEntryRepository : JpaRepository<LedgerEntry, Long> {
    @Query("select coalesce(sum(e.amount), 0) from LedgerEntry e where e.account.id = :accountId")
    fun sumByAccountId(@Param("accountId") accountId: Long): Long

    @Query("select e from LedgerEntry e where e.transfer.id = :transferId order by e.id")
    fun findByTransferId(@Param("transferId") transferId: UUID): List<LedgerEntry>

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
