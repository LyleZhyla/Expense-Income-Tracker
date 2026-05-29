CREATE TABLE IF NOT EXISTS transactions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    type TEXT NOT NULL CHECK (type IN ('income', 'expense')),
    title TEXT NOT NULL,
    category TEXT NOT NULL DEFAULT 'General',
    amount NUMERIC NOT NULL,
    transaction_date TEXT NOT NULL,
    notes TEXT,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_transactions_type
ON transactions (type);

CREATE INDEX IF NOT EXISTS idx_transactions_category
ON transactions (category);

CREATE INDEX IF NOT EXISTS idx_transactions_date
ON transactions (transaction_date);
