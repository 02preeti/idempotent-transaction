
CREATE TABLE account_balances (
                                  account_id VARCHAR(100) PRIMARY KEY,
                                  balance NUMERIC(19, 2) NOT NULL DEFAULT 0
                                      CHECK (balance >= 0),
                                  currency VARCHAR(3) NOT NULL DEFAULT 'INR',
                                  next_sequence BIGINT NOT NULL DEFAULT 1
                                      CHECK (next_sequence >= 1),
                                  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE transactions (
                              transaction_id VARCHAR(100) PRIMARY KEY,
                              request_id VARCHAR(100) NOT NULL,
                              account_id VARCHAR(100) NOT NULL
                                  REFERENCES account_balances(account_id),
                              transaction_type VARCHAR(10) NOT NULL
                                  CHECK (transaction_type IN ('CREDIT', 'DEBIT')),
                              amount NUMERIC(19, 2) NOT NULL
                                  CHECK (amount > 0),
                              currency VARCHAR(3) NOT NULL
                                  CHECK (currency = 'INR'),
                              sequence_number BIGINT NOT NULL
                                  CHECK (sequence_number > 0),
                              status VARCHAR(20) NOT NULL,
                              retry_count INTEGER NOT NULL DEFAULT 0,
                              error_message TEXT,
                              next_retry_at TIMESTAMPTZ,
                              created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                              updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

                              CONSTRAINT uq_account_sequence
                                  UNIQUE (account_id, sequence_number)
);

CREATE INDEX idx_transactions_worker
    ON transactions (status, next_retry_at, account_id, sequence_number);

CREATE INDEX idx_transactions_account
    ON transactions (account_id, sequence_number);
