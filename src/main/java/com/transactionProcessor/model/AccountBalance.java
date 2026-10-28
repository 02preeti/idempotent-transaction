
package com.transactionProcessor.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "account_balances")
public class AccountBalance {

    @Id
    @Column(name = "account_id", length = 100)
    private String accountId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(nullable = false, length = 3)
    private String currency = "INR";

    @Column(name = "next_sequence", nullable = false)
    private long nextSequence = 1;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected AccountBalance() {
    }

    public AccountBalance(String accountId, String currency) {
        this.accountId = accountId;
        this.currency = currency;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public String getAccountId() { return accountId; }
    public BigDecimal getBalance() { return balance; }
    public String getCurrency() { return currency; }
    public long getNextSequence() { return nextSequence; }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public void setNextSequence(long nextSequence) {
        this.nextSequence = nextSequence;
    }
}
