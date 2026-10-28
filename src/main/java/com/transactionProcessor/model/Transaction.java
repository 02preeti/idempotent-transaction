
package com.transactionProcessor.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "transactions",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_account_sequence",
                columnNames = {"account_id", "sequence_number"}
        ),
        indexes = {
                @Index(name = "idx_transactions_account",
                        columnList = "account_id, sequence_number")
        }
)
public class Transaction {

    @Id
    @Column(name = "transaction_id", length = 100)
    private String transactionId;

    @Column(name = "request_id", nullable = false, length = 100)
    private String requestId;

    @Column(name = "account_id", nullable = false, length = 100)
    private String accountId;

    @Column(name = "transaction_type", nullable = false, length = 10)
    private String transactionType;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "sequence_number", nullable = false)
    private long sequenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "created_at", nullable = false,
            insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Transaction() {
    }

    public Transaction(
            String transactionId,
            String requestId,
            String accountId,
            String transactionType,
            BigDecimal amount,
            String currency,
            long sequenceNumber) {
        this.transactionId = transactionId;
        this.requestId = requestId;
        this.accountId = accountId;
        this.transactionType = transactionType;
        this.amount = amount;
        this.currency = currency;
        this.sequenceNumber = sequenceNumber;
        this.status = TransactionStatus.RECEIVED;
        this.retryCount = 0;
        this.updatedAt = Instant.now();
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (status == null) {
            status = TransactionStatus.RECEIVED;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public String getTransactionId() { return transactionId; }
    public String getRequestId() { return requestId; }
    public String getAccountId() { return accountId; }
    public String getTransactionType() { return transactionType; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public long getSequenceNumber() { return sequenceNumber; }
    public TransactionStatus getStatus() { return status; }
    public int getRetryCount() { return retryCount; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getNextRetryAt() { return nextRetryAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setStatus(TransactionStatus status) {
        this.status = status;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public void setNextRetryAt(Instant nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }
}
