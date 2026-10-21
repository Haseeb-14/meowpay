package com.meowpay.transfer

import com.meowpay.account.Account
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.UuidGenerator
import java.time.Instant
import java.util.UUID

/** A completed movement of treats from one account to another. Immutable once written. */
@Entity
@Table(name = "transfers")
class Transfer(
    @Column(name = "idempotency_key", nullable = false, columnDefinition = "text")
    val idempotencyKey: String,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_account_id", nullable = false)
    val fromAccount: Account,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_account_id", nullable = false)
    val toAccount: Account,

    @Column(nullable = false)
    val amount: Long,

    @Column(columnDefinition = "text")
    val note: String?,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    /** Assigned by the database; the stable ordering key for history. */
    @Column(insertable = false, updatable = false)
    val seq: Long? = null,

    @Id
    @GeneratedValue
    @UuidGenerator
    var id: UUID? = null,
)
