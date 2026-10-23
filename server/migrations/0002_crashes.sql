-- One row per distinct crash (same error at the same place), so repeats don't open new GitHub issues.
CREATE TABLE crash (
  signature TEXT PRIMARY KEY,
  issue INTEGER,
  count INTEGER NOT NULL DEFAULT 1,
  first_at INTEGER NOT NULL,
  last_at INTEGER NOT NULL
);
