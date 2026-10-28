
package com.transactionProcessor.worker;

import com.transactionProcessor.model.TransactionStatus;
import com.transactionProcessor.repository.TransactionRepository;
import com.transactionProcessor.service.TransactionService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TransactionWorker {

    private static final Logger log =
            LoggerFactory.getLogger(TransactionWorker.class);

    private final TransactionService transactionService;
    private final TransactionRepository transactionRepository;

    public TransactionWorker(
            TransactionService transactionService,
            TransactionRepository transactionRepository) {
        this.transactionService = transactionService;
        this.transactionRepository = transactionRepository;
    }

    // Automatically process transactions every second.
    @Scheduled(fixedDelay = 1000)
    public void processTransactions() {
        poll();
    }

    // Can also be called manually from the controller.
    public void poll() {
        List<String> accountIds =
                transactionRepository.findAccountsWithWork(
                        List.of(
                                TransactionStatus.RECEIVED,
                                TransactionStatus.PENDING,
                                TransactionStatus.RETRY_WAIT
                        ));

        int processedCount = 0;

        for (String accountId : accountIds) {
            try {
                boolean progressed =
                        transactionService.processNextForAccount(accountId);

                if (progressed) {
                    processedCount++;
                    log.info(
                            "Advanced processing for account {}",
                            accountId);
                }
            } catch (Exception ex) {
                log.error(
                        "Failed to process account {}",
                        accountId,
                        ex);
            }
        }

        log.info(
                "Polling completed. Accounts advanced: {}",
                processedCount);
    }
}
