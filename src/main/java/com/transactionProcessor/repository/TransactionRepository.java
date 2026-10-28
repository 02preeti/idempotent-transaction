
package com.transactionProcessor.repository;

import com.transactionProcessor.model.Transaction;
import com.transactionProcessor.model.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TransactionRepository
        extends JpaRepository<Transaction, String> {

    Optional<Transaction> findByAccountIdAndSequenceNumber(
            String accountId, long sequenceNumber);

//    List<Transaction> findByAccountIdOrderBySequenceNumber(
//            String accountId);

    long countByStatus(TransactionStatus status);

    @Query("""
        select distinct t.accountId
        from Transaction t
        where t.status in :statuses
        order by t.accountId
        """)
    List<String> findAccountsWithWork(
            @Param("statuses") List<TransactionStatus> statuses);
}
