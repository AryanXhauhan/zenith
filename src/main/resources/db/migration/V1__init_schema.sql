-- ============================================================
--  ZENITH SETTLEMENT ENGINE  –  Database Schema V1
--  Double-Entry Bookkeeping + Idempotency + API Keys
-- ============================================================

-- ── Extensions ──────────────────────────────────────────────
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ── API Keys ────────────────────────────────────────────────
CREATE TABLE api_keys (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    key_hash        VARCHAR(64)  NOT NULL UNIQUE,   -- SHA-256 of the actual key
    name            VARCHAR(255) NOT NULL,
    tier            VARCHAR(50)  NOT NULL DEFAULT 'FREE',   -- FREE | PRO | ENTERPRISE
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMPTZ,
    last_used_at    TIMESTAMPTZ
);

CREATE INDEX idx_api_keys_hash ON api_keys(key_hash);

-- ── Accounts ────────────────────────────────────────────────
CREATE TABLE accounts (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    owner_id        VARCHAR(255) NOT NULL,                  -- External user/entity reference
    account_number  VARCHAR(34)  NOT NULL UNIQUE,           -- IBAN-like identifier
    account_name    VARCHAR(255),
    currency        VARCHAR(3)   NOT NULL DEFAULT 'USD',
    balance         NUMERIC(20, 8) NOT NULL DEFAULT 0.00,
    account_type    VARCHAR(50)  NOT NULL DEFAULT 'CHECKING', -- CHECKING | SAVINGS | ESCROW | FEE
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE | FROZEN | CLOSED
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_balance_non_negative CHECK (balance >= 0)
);

CREATE INDEX idx_accounts_owner    ON accounts(owner_id);
CREATE INDEX idx_accounts_number   ON accounts(account_number);
CREATE INDEX idx_accounts_currency ON accounts(currency);

-- ── Ledger Entries (Double-Entry Core) ──────────────────────
-- Every financial event creates EXACTLY 2 rows here:
--   one DEBIT  (amount < 0 from the source account's perspective)
--   one CREDIT (amount > 0 from the destination account's perspective)
-- The sum of all entries for one transaction_id MUST equal ZERO.
CREATE TABLE ledger_entries (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    transaction_id  UUID         NOT NULL,              -- Groups the two legs
    account_id      UUID         NOT NULL REFERENCES accounts(id),
    entry_type      VARCHAR(6)   NOT NULL,              -- DEBIT | CREDIT
    amount          NUMERIC(20, 8) NOT NULL,            -- Always POSITIVE
    currency        VARCHAR(3)   NOT NULL,
    description     VARCHAR(500),
    metadata        JSONB,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_amount_positive CHECK (amount > 0)
);

CREATE INDEX idx_entries_txn_id    ON ledger_entries(transaction_id);
CREATE INDEX idx_entries_account   ON ledger_entries(account_id);
CREATE INDEX idx_entries_created   ON ledger_entries(created_at DESC);

-- ── Transactions (Header / Summary) ─────────────────────────
CREATE TABLE transactions (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    idempotency_key     VARCHAR(255) UNIQUE,             -- Deduplication key
    reference           VARCHAR(255) NOT NULL UNIQUE,   -- Human-readable ref
    status              VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    -- PENDING | PROCESSING | COMPLETED | FAILED | REVERSED
    amount              NUMERIC(20, 8) NOT NULL,
    currency            VARCHAR(3)   NOT NULL,
    source_account_id   UUID         NOT NULL REFERENCES accounts(id),
    dest_account_id     UUID         NOT NULL REFERENCES accounts(id),
    description         VARCHAR(500),
    metadata            JSONB,
    blockchain_tx_hash  VARCHAR(66),                    -- Ethereum tx hash (0x...)
    blockchain_confirmed BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    completed_at        TIMESTAMPTZ,

    CONSTRAINT chk_amount_positive    CHECK (amount > 0),
    CONSTRAINT chk_diff_accounts      CHECK (source_account_id <> dest_account_id)
);

CREATE INDEX idx_txn_idempotency  ON transactions(idempotency_key);
CREATE INDEX idx_txn_reference    ON transactions(reference);
CREATE INDEX idx_txn_status       ON transactions(status);
CREATE INDEX idx_txn_created      ON transactions(created_at DESC);
CREATE INDEX idx_txn_blockchain   ON transactions(blockchain_tx_hash) WHERE blockchain_tx_hash IS NOT NULL;

-- ── Idempotency Records ──────────────────────────────────────
CREATE TABLE idempotency_records (
    idempotency_key     VARCHAR(255) PRIMARY KEY,
    response_status     INTEGER      NOT NULL,
    response_body       TEXT         NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    expires_at          TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_idempotency_expires ON idempotency_records(expires_at);

-- ── Audit Log ────────────────────────────────────────────────
CREATE TABLE audit_log (
    id          BIGSERIAL    PRIMARY KEY,
    entity_type VARCHAR(50)  NOT NULL,
    entity_id   UUID         NOT NULL,
    action      VARCHAR(50)  NOT NULL,
    actor       VARCHAR(255),
    old_value   JSONB,
    new_value   JSONB,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_entity ON audit_log(entity_type, entity_id);
CREATE INDEX idx_audit_created ON audit_log(created_at DESC);

-- ── Auto-update updated_at trigger ──────────────────────────
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_accounts_updated_at
    BEFORE UPDATE ON accounts
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trg_transactions_updated_at
    BEFORE UPDATE ON transactions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ── Seed system accounts ─────────────────────────────────────
INSERT INTO accounts (owner_id, account_number, currency, balance, account_type)
VALUES
    ('SYSTEM', 'ZNT-SYSTEM', 'USD', 0, 'FEE'),
    ('SYSTEM', 'ZNT-SYS-ESC-0001', 'USD', 0, 'ESCROW');
