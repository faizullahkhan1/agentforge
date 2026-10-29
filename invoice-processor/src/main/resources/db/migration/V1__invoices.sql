-- Lives in the "invoices" schema (see spring.flyway.schemas). Queries name the schema explicitly
-- (invoices.invoice), so they work no matter which schema a connection defaults to.

CREATE TABLE invoices.invoice (
    id              BIGSERIAL     PRIMARY KEY,
    status          VARCHAR(20)   NOT NULL,   -- READY | NEEDS_REVIEW | APPROVED | REJECTED
    file_name       TEXT,
    file_sha256     CHAR(64)      NOT NULL,   -- same file uploaded twice is refused before any AI call
    extracted_from  VARCHAR(10)   NOT NULL,   -- TEXT | VISION
    vendor_name     TEXT,
    invoice_number  TEXT,
    issue_date      DATE,                     -- NULL when the extracted value wasn't a valid date
    due_date        DATE,
    currency        VARCHAR(10),
    subtotal        NUMERIC(14, 2),
    tax             NUMERIC(14, 2),
    total           NUMERIC(14, 2),
    issues          JSONB         NOT NULL DEFAULT '[]',
    review_note     TEXT,
    reviewed_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_invoice_file ON invoices.invoice (file_sha256);
CREATE INDEX ix_invoice_vendor_number ON invoices.invoice (lower(vendor_name), lower(invoice_number));
CREATE INDEX ix_invoice_status ON invoices.invoice (status);

CREATE TABLE invoices.invoice_line (
    id               BIGSERIAL      PRIMARY KEY,
    invoice_id       BIGINT         NOT NULL REFERENCES invoices.invoice (id) ON DELETE CASCADE,
    line_index       INT            NOT NULL,
    description      TEXT,
    quantity         NUMERIC(14, 4),
    unit_price       NUMERIC(14, 4),
    amount           NUMERIC(14, 2),
    category         VARCHAR(40),
    category_reason  TEXT,
    UNIQUE (invoice_id, line_index)
);
