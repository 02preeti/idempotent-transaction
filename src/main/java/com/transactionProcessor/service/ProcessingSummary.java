
package com.transactionProcessor.service;

public record ProcessingSummary(
        int processedThisRun,
        int failedThisRun,
        int retriedThisRun,
        long totalReceived,
        long totalPending,
        long totalProcessed,
        long totalFailed,
        long totalRetryWaiting
) {}
