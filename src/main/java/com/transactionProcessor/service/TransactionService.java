
package com.transactionProcessor.service;

import com.transactionProcessor.model.AccountBalance;
import com.transactionProcessor.model.Transaction;
import com.transactionProcessor.model.TransactionStatus;
import com.transactionProcessor.repository.AccountBalanceRepository;
import com.transactionProcessor.repository.TransactionRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class TransactionService {

    private static final List<String> TYPES =
            List.of("CREDIT", "DEBIT");

    private final TransactionRepository transactions;
    private final AccountBalanceRepository accounts;
    private final int maxRetries;
    private final long retryDelayMs;

    public TransactionService(
            TransactionRepository transactions,
            AccountBalanceRepository accounts,
            @Value("${transaction.worker.max-retries:3}")
            int maxRetries,
            @Value("${transaction.worker.retry-delay-ms:5000}")
            long retryDelayMs) {
        this.transactions = transactions;
        this.accounts = accounts;
        this.maxRetries = maxRetries;
        this.retryDelayMs = retryDelayMs;
    }

    @Transactional
    public Transaction submit(Transaction transaction) {
        validate(transaction);

        Optional<Transaction> existing =
                transactions.findById(transaction.getTransactionId());

        if (existing.isPresent()) {
            Transaction saved = existing.get();

            if (!sameRequest(saved, transaction)) {
                throw new IllegalArgumentException(
                        "Transaction ID already exists with different data");
            }

            Transaction duplicate = copy(saved);
            duplicate.setStatus(TransactionStatus.DUPLICATE);
            return duplicate;
        }

        Optional<Transaction> sameSequence =
                transactions.findByAccountIdAndSequenceNumber(
                        transaction.getAccountId(),
                        transaction.getSequenceNumber());

        if (sameSequence.isPresent()) {
            throw new IllegalArgumentException(
                    "Sequence number already assigned for this account");
        }

        AccountBalance account = accounts.findById(
                transaction.getAccountId()).orElse(null);

        if (account == null) {
            account = accounts.saveAndFlush(
                    new AccountBalance(
                            transaction.getAccountId(),
                            transaction.getCurrency()));
        } else if (!account.getCurrency().equals(
                transaction.getCurrency())) {
            throw new IllegalArgumentException(
                    "Account currency does not match transaction currency");
        }

        transaction.setStatus(TransactionStatus.PENDING);
        transaction.setRetryCount(0);
        transaction.setErrorMessage(null);
        transaction.setNextRetryAt(null);

        return copy(transactions.saveAndFlush(transaction));
    }

    /*
     * Processes at most the next expected sequence for an account.
     * Each invocation has its own database transaction.
     */
    @Transactional
    public boolean processNextForAccount(String accountId) {
        AccountBalance account =
                accounts.lockByAccountId(accountId).orElse(null);

        if (account == null) {
            return false;
        }

        long expected = account.getNextSequence();

        Transaction transaction = transactions
                .findByAccountIdAndSequenceNumber(accountId, expected)
                .orElse(null);

        // A missing sequence blocks later requests.
        if (transaction == null) {
            return false;
        }

        if (transaction.getStatus() == TransactionStatus.PROCESSED
                || transaction.getStatus() == TransactionStatus.FAILED) {
            account.setNextSequence(expected + 1);
            return true;
        }

        if (transaction.getStatus() == TransactionStatus.RETRY_WAIT
                && transaction.getNextRetryAt() != null
                && transaction.getNextRetryAt().isAfter(Instant.now())) {
            return false;
        }

        if (transaction.getStatus() != TransactionStatus.PENDING
                && transaction.getStatus() != TransactionStatus.RECEIVED
                && transaction.getStatus() != TransactionStatus.RETRY_WAIT) {
            return false;
        }

        transaction.setStatus(TransactionStatus.PROCESSING);
        transaction.setErrorMessage(null);

        try {
            applyTransaction(account, transaction);

            transaction.setStatus(TransactionStatus.PROCESSED);
            transaction.setErrorMessage(null);
            transaction.setNextRetryAt(null);

            account.setNextSequence(expected + 1);
            return true;
        } catch (IllegalArgumentException permanentFailure) {
            /*
             * Business failures consume their sequence in this demo.
             * A later sequence can proceed after the failure is recorded.
             */
            transaction.setStatus(TransactionStatus.FAILED);
            transaction.setErrorMessage(permanentFailure.getMessage());
            transaction.setNextRetryAt(null);

            account.setNextSequence(expected + 1);
            return true;
        }
    }

    private void applyTransaction(
            AccountBalance account, Transaction transaction) {

        BigDecimal balance = account.getBalance();
        BigDecimal amount = transaction.getAmount();

        if (!account.getCurrency().equals(transaction.getCurrency())) {
            throw new IllegalArgumentException(
                    "Account currency does not match transaction currency");
        }

        switch (transaction.getTransactionType()) {
            case "CREDIT" ->
                    account.setBalance(balance.add(amount));

            case "DEBIT" -> {
                if (balance.compareTo(amount) < 0) {
                    throw new IllegalArgumentException(
                            "Insufficient account balance");
                }
                account.setBalance(balance.subtract(amount));
            }

            default -> throw new IllegalArgumentException(
                    "Unsupported transaction type");
        }
    }

    /*
     * Called separately after a transient processing exception has caused
     * the processing transaction to roll back.
     */
    @Transactional
    public void scheduleRetry(String accountId) {
        AccountBalance account =
                accounts.lockByAccountId(accountId).orElse(null);

        if (account == null) {
            return;
        }

        transactions.findByAccountIdAndSequenceNumber(
                accountId, account.getNextSequence()
        ).ifPresent(transaction -> {
            if (transaction.getStatus() == TransactionStatus.PROCESSED
                    || transaction.getStatus() == TransactionStatus.FAILED) {
                return;
            }

            int nextAttempt = transaction.getRetryCount() + 1;
            transaction.setRetryCount(nextAttempt);

            if (nextAttempt > maxRetries) {
                transaction.setStatus(TransactionStatus.FAILED);
                transaction.setErrorMessage(
                        "Retry limit exhausted after transient failures");
                transaction.setNextRetryAt(null);

                // Explicit policy: exhausted retries consume the sequence.
                account.setNextSequence(account.getNextSequence() + 1);
            } else {
                transaction.setStatus(TransactionStatus.RETRY_WAIT);
                transaction.setErrorMessage(
                        "Transient processing failure; retry scheduled");
                transaction.setNextRetryAt(
                        Instant.now().plusMillis(
                                retryDelayMs * nextAttempt));
            }
        });
    }

    @Transactional(readOnly = true)
    public Optional<Transaction> findById(String id) {
        return transactions.findById(id).map(this::copy);
    }

    @Transactional(readOnly = true)
    public BigDecimal getBalance(String accountId) {
        return accounts.findById(accountId)
                .map(AccountBalance::getBalance)
                .orElse(BigDecimal.ZERO);
    }

    @Transactional(readOnly = true)
    public List<Transaction> findAll() {
        return transactions.findAll().stream()
                .map(this::copy)
                .sorted(Comparator.comparing(
                        Transaction::getTransactionId))
                .toList();
    }

    @Transactional(readOnly = true)
    public ProcessingSummary getSummary(
            int processed, int failed, int retried) {
        return new ProcessingSummary(
                processed,
                failed,
                retried,
                transactions.countByStatus(TransactionStatus.RECEIVED),
                transactions.countByStatus(TransactionStatus.PENDING),
                transactions.countByStatus(TransactionStatus.PROCESSED),
                transactions.countByStatus(TransactionStatus.FAILED),
                transactions.countByStatus(TransactionStatus.RETRY_WAIT)
        );
    }

    private void validate(Transaction transaction) {
        if (transaction == null
                || isBlank(transaction.getTransactionId())
                || isBlank(transaction.getAccountId())
                || isBlank(transaction.getCurrency())) {
            throw new IllegalArgumentException(
                    "Transaction ID, account ID and currency are required");
        }

        if (transaction.getAmount() == null
                || transaction.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Amount must be greater than zero");
        }

        if (transaction.getSequenceNumber() < 1) {
            throw new IllegalArgumentException(
                    "Sequence number must be at least 1");
        }

        if (!TYPES.contains(transaction.getTransactionType())) {
            throw new IllegalArgumentException(
                    "Transaction type must be CREDIT or DEBIT");
        }

        if (!"INR".equals(transaction.getCurrency())) {
            throw new IllegalArgumentException(
                    "Only INR is supported in this demo");
        }
    }

    private boolean sameRequest(Transaction first, Transaction second) {
        return first.getAccountId().equals(second.getAccountId())
                && first.getTransactionType().equals(
                second.getTransactionType())
                && first.getAmount().compareTo(second.getAmount()) == 0
                && first.getCurrency().equals(second.getCurrency())
                && first.getSequenceNumber() == second.getSequenceNumber();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private Transaction copy(Transaction source) {
        Transaction result = new Transaction(
                source.getTransactionId(),
                source.getRequestId(),
                source.getAccountId(),
                source.getTransactionType(),
                source.getAmount(),
                source.getCurrency(),
                source.getSequenceNumber());

        result.setStatus(source.getStatus());
        result.setRetryCount(source.getRetryCount());
        result.setErrorMessage(source.getErrorMessage());
        result.setNextRetryAt(source.getNextRetryAt());
        return result;
    }
}
