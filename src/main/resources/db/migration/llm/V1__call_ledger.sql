-- Cost ledger of the llm module (AD-8, AD-12). Every LLM call is one row and one Turn: reserved before the call,
-- settled with the real cost afterwards (FAILED calls cost 0 but still count as a Turn).
CREATE SCHEMA IF NOT EXISTS llm;

CREATE TABLE llm.call_ledger
(
  id                 UUID PRIMARY KEY,
  task_id            UUID                     NOT NULL,
  model              TEXT                     NOT NULL,
  called_at          TIMESTAMP WITH TIME ZONE NOT NULL,
  -- Calendar month in alfred.llm.budget-zone, e.g. 2026-10; the budget is counted per month.
  budget_month       CHAR(7)                  NOT NULL,
  status             TEXT                     NOT NULL CHECK (status IN ('RESERVED', 'SETTLED', 'FAILED')),
  reserved_usd       NUMERIC(14, 8)           NOT NULL,
  cost_usd           NUMERIC(14, 8),
  input_tokens       BIGINT,
  output_tokens      BIGINT,
  cache_read_tokens  BIGINT,
  cache_write_tokens BIGINT
);

CREATE INDEX call_ledger_by_task ON llm.call_ledger (task_id);
CREATE INDEX call_ledger_by_month ON llm.call_ledger (budget_month);

-- The 80% warning is given once per month.
CREATE TABLE llm.budget_warning
(
  budget_month CHAR(7) PRIMARY KEY,
  warned_at    TIMESTAMP WITH TIME ZONE NOT NULL
);
