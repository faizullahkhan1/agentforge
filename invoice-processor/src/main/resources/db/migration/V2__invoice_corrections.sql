-- V1 already ran on existing databases and must never be edited, so schema changes go in a new file.

-- Audit trail: every value a reviewer changed, what it was, and what it became.
-- The invoice rows hold the corrected values; this table keeps the history.
CREATE TABLE invoices.invoice_correction (
    id            BIGSERIAL    PRIMARY KEY,
    invoice_id    BIGINT       NOT NULL REFERENCES invoices.invoice (id) ON DELETE CASCADE,
    field         TEXT         NOT NULL,   -- e.g. issueDate, line 1 quantity
    old_value     TEXT,
    new_value     TEXT,
    note          TEXT,
    corrected_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_invoice_correction_invoice ON invoices.invoice_correction (invoice_id);
