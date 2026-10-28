
package com.transactionProcessor.dto;

import com.transactionProcessor.model.Transaction;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record TransactionRequest(
        @NotBlank @Size(max = 100)
        String transactionId,

        @NotBlank @Size(max = 100)
        String requestId,

        @NotBlank @Size(max = 100)
        String accountId,

        @NotBlank
        String transactionType,

        @NotNull @DecimalMin(value = "0.00", inclusive = false)
        @Digits(integer = 17, fraction = 2)
        BigDecimal amount,

        @NotBlank
        String currency,

        @Min(1)
        long sequenceNumber
) {
    public Transaction toEntity() {
        return new Transaction(
                transactionId,
                requestId,
                accountId,
                transactionType,
                amount,
                currency,
                sequenceNumber);
    }
}
