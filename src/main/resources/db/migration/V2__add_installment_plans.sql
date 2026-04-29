CREATE TABLE installment_plans (
    id                  UUID PRIMARY KEY,
    account_number      VARCHAR(34) NOT NULL,
    total_amount        NUMERIC(20, 8) NOT NULL,
    installment_amount  NUMERIC(20, 8) NOT NULL,
    total_installments  INTEGER NOT NULL,
    paid_installments   INTEGER NOT NULL DEFAULT 0,
    remaining_amount    NUMERIC(20, 8) NOT NULL,
    next_due_date       TIMESTAMPTZ NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_installments_account ON installment_plans(account_number);
CREATE INDEX idx_installments_status  ON installment_plans(status);
