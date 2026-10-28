package com.transactionProcessor.model;

public enum TransactionStatus {
    RECEIVED,
    PENDING,
    PROCESSING,
    RETRY_WAIT,
    PROCESSED,
    FAILED,
    DUPLICATE
}
