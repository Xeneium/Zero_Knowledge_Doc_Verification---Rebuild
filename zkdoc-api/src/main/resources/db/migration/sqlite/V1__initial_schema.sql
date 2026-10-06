CREATE TABLE document_records (
    id TEXT PRIMARY KEY,
    content BLOB NOT NULL,
    digest BLOB NOT NULL UNIQUE,
    signature BLOB NOT NULL,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
