-- One rating per phone per recording. Notes are private; only counts are ever shown to others.
CREATE TABLE rating (
    device TEXT NOT NULL,
    recording INTEGER NOT NULL,
    up INTEGER NOT NULL,
    problems TEXT NOT NULL DEFAULT '',
    note TEXT NOT NULL DEFAULT '',
    chapter INTEGER,
    created_at INTEGER NOT NULL,
    PRIMARY KEY (device, recording)
);
CREATE INDEX rating_recording ON rating (recording);
