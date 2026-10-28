
package com.transactionProcessor.repository;

import com.transactionProcessor.model.AccountBalance;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AccountBalanceRepository
        extends JpaRepository<AccountBalance, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select a from AccountBalance a
        where a.accountId = :accountId
        """)
    Optional<AccountBalance> lockByAccountId(
            @Param("accountId") String accountId);
}
