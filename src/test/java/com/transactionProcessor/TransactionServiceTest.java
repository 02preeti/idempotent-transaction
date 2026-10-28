
package com.transactionProcessor;

import com.transactionProcessor.model.AccountBalance;
import com.transactionProcessor.model.Transaction;
import com.transactionProcessor.model.TransactionStatus;
import com.transactionProcessor.repository.AccountBalanceRepository;
import com.transactionProcessor.repository.TransactionRepository;
import com.transactionProcessor.service.TransactionService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactions;

    @Mock
    private AccountBalanceRepository accounts;

    @InjectMocks
    private TransactionService service;

    @BeforeEach
    void setUp() {
        // @InjectMocks supplies the repositories and default int/long values.
        // The service constructor receives maxRetries = 0 and retryDelayMs = 0.
        // Tests that require retries should use explicit constructor setup.
    }

    private Transaction transaction(
            String id,
            String requestId,
            long sequence,
            String account,
            String type,
            String amount) {

        return new Transaction(
                id,
                requestId,
                account,
                type,
                new BigDecimal(amount),
                "INR",
                sequence);
    }

    @Test
    void invalidAmountShouldBeRejected() {
        Transaction invalid = transaction(
                "TXN-2004", "REQ-4", 4,
                "ACC-202", "DEBIT", "-10.00");

        assertThrows(
                IllegalArgumentException.class,
                () -> service.submit(invalid));

        verifyNoInteractions(transactions, accounts);
    }

    @Test
    void duplicateTransactionShouldBeDetected() {
        Transaction original = transaction(
                "TXN-2002", "REQ-2", 2,
                "ACC-201", "DEBIT", "40.00");

        original.setStatus(TransactionStatus.PROCESSED);

        when(transactions.findById("TXN-2002"))
                .thenReturn(Optional.of(original));

        Transaction duplicate = service.submit(transaction(
                "TXN-2002", "REQ-2-DUP", 2,
                "ACC-201", "DEBIT", "40.00"));

        assertEquals(
                TransactionStatus.DUPLICATE,
                duplicate.getStatus());

        verify(transactions, never()).saveAndFlush(any());
    }

    @Test
    void conflictingDuplicateShouldBeRejected() {
        Transaction original = transaction(
                "TXN-2002", "REQ-2", 2,
                "ACC-201", "DEBIT", "40.00");

        when(transactions.findById("TXN-2002"))
                .thenReturn(Optional.of(original));

        assertThrows(
                IllegalArgumentException.class,
                () -> service.submit(transaction(
                        "TXN-2002", "REQ-2-DUP", 2,
                        "ACC-201", "DEBIT", "50.00")));
    }

    @Test
    void submitShouldSaveTransactionAsPending() {
        Transaction input = transaction(
                "TXN-2001", "REQ-1", 1,
                "ACC-201", "CREDIT", "100.00");

        when(transactions.findById("TXN-2001"))
                .thenReturn(Optional.empty());

        when(transactions.findByAccountIdAndSequenceNumber(
                "ACC-201", 1L))
                .thenReturn(Optional.empty());

        when(accounts.findById("ACC-201"))
                .thenReturn(Optional.empty());

        when(accounts.saveAndFlush(any(AccountBalance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(transactions.saveAndFlush(input))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Transaction saved = service.submit(input);

        assertEquals(TransactionStatus.PENDING, saved.getStatus());
        assertEquals("TXN-2001", saved.getTransactionId());

        verify(transactions).saveAndFlush(input);
    }

    @Test
    void laterSequenceShouldWaitWhenEarlierSequenceIsMissing() {
        AccountBalance account =
                new AccountBalance("ACC-201", "INR");

        when(accounts.lockByAccountId("ACC-201"))
                .thenReturn(Optional.of(account));

        // AccountBalance must start with nextSequence = 1.
        // Sequence 2 cannot be processed while sequence 1 is missing.
        when(transactions.findByAccountIdAndSequenceNumber(
                "ACC-201", 1L))
                .thenReturn(Optional.empty());

        boolean processed = service.processNextForAccount("ACC-201");

        assertFalse(processed);
        assertEquals(1L, account.getNextSequence());

        verify(transactions, never()).saveAndFlush(any());
    }

    @Test
    void missingAccountShouldNotBeProcessed() {
        when(accounts.lockByAccountId("ACC-201"))
                .thenReturn(Optional.empty());

        assertFalse(service.processNextForAccount("ACC-201"));
    }

    @Test
    void getBalanceShouldReturnZeroWhenAccountDoesNotExist() {
        when(accounts.findById("ACC-203"))
                .thenReturn(Optional.empty());

        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(service.getBalance("ACC-203")));
    }
}
