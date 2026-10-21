package com.meowpay.transfer

import com.meowpay.account.Account
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant

enum class LedgerKind { TOPUP, TRANSFER_OUT, TRANSFER_IN }

/**
 * One line of the double-entry ledger. A transfer writes two rows that sum to zero;
 * a top-up writes one. `balanceAfter` is the account balance once this line applied.
 */
@Entity
@Table(name = "ledger_entries")
class LedgerEntry(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    val account: Account,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transfer_id")
    val transfer: Transfer?,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "text")
    val kind: LedgerKind,

    /** Signed: negative leaves the account, positive enters it. */
    @Column(nullable = false)
    val amount: Long,

    @Column(name = "balance_after", nullable = false)
    val balanceAfter: Long,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
)
