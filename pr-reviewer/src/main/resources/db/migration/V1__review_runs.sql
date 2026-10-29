-- Flyway runs every V<n>__*.sql file in this folder once, in order, and records it in
-- the flyway_schema_history table. Never edit a migration that already ran: add V2, V3... instead.

-- One row per review: a pasted snippet (/api/review) or a pull request (manual API call or webhook)
CREATE TABLE review_run (
    id             BIGSERIAL    PRIMARY KEY,
    kind           VARCHAR(20)  NOT NULL,   -- CODE | PULL_REQUEST
    triggered_by   VARCHAR(20)  NOT NULL,   -- API | WEBHOOK
    status         VARCHAR(20)  NOT NULL,   -- RUNNING | COMPLETED | FAILED
    repository     VARCHAR(200),
    pr_number      INT,
    head_sha       VARCHAR(40),
    reviewed_since VARCHAR(40),
    dry_run        BOOLEAN      NOT NULL DEFAULT FALSE,
    comment_count  INT,
    review_url     TEXT,
    error          TEXT,
    started_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    finished_at    TIMESTAMPTZ,
    total_millis   BIGINT
);
CREATE INDEX idx_review_run_started_at ON review_run (started_at DESC);
CREATE INDEX idx_review_run_pr ON review_run (repository, pr_number);

-- The trace: every agent call made during a run, with timing and errors
CREATE TABLE agent_call (
    id        BIGSERIAL    PRIMARY KEY,
    run_id    BIGINT       NOT NULL REFERENCES review_run (id) ON DELETE CASCADE,
    file_path TEXT,
    agent     VARCHAR(100) NOT NULL,
    findings  INT          NOT NULL,
    millis    BIGINT       NOT NULL,
    error     TEXT
);
CREATE INDEX idx_agent_call_run ON agent_call (run_id);

-- Every finding, including the ones that were not posted, so you can see what the judge threw out
CREATE TABLE finding (
    id           BIGSERIAL   PRIMARY KEY,
    run_id       BIGINT      NOT NULL REFERENCES review_run (id) ON DELETE CASCADE,
    file_path    TEXT,
    status       VARCHAR(20) NOT NULL,   -- KEPT | OUTSIDE_DIFF | DROPPED
    line_number  INT         NOT NULL,
    severity     VARCHAR(20),
    category     VARCHAR(30),
    title        TEXT,
    code         TEXT,
    explanation  TEXT,
    suggestion   TEXT,
    found_by     VARCHAR(50),            -- only known for dropped findings (from the judge's input)
    judge_reason TEXT
);
CREATE INDEX idx_finding_run ON finding (run_id);
