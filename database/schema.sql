-- Run in an existing database owned by the Flux role:
-- psql -h localhost -U flux -d flux --single-transaction -v ON_ERROR_STOP=1 -f database/schema.sql
-- DatabaseManager executes this same file transactionally on its JDBC worker.

CREATE TABLE IF NOT EXISTS history (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title VARCHAR(512) NOT NULL,
    url VARCHAR(2048) NOT NULL CHECK (url ~ '^https?://'),
    visit_timestamp TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS history_visited_idx ON history (visit_timestamp DESC, id DESC);
CREATE INDEX IF NOT EXISTS history_url_idx ON history (url);

CREATE TABLE IF NOT EXISTS bookmarks (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title VARCHAR(512) NOT NULL CHECK (length(btrim(title)) > 0),
    url VARCHAR(2048) NOT NULL UNIQUE CHECK (url ~ '^https?://'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    category VARCHAR(80) NOT NULL DEFAULT 'Unsorted' CHECK (length(btrim(category)) > 0)
);
CREATE INDEX IF NOT EXISTS bookmarks_created_idx ON bookmarks (created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS bookmarks_category_idx ON bookmarks (category);

-- Seed only when creating the table: deleted starter tiles must stay deleted.
-- "quick_dial" in the brief and "speed_dial" refer to this same feature.
DO $schema$
BEGIN
    IF to_regclass('speed_dial') IS NULL THEN
        CREATE TABLE speed_dial (
            id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
            title VARCHAR(512) NOT NULL CHECK (length(btrim(title)) > 0),
            url VARCHAR(2048) NOT NULL UNIQUE CHECK (url ~ '^https?://'),
            created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
            position INTEGER NOT NULL DEFAULT 0 CHECK (position >= 0)
        );
        INSERT INTO speed_dial (title, url, position) VALUES
            ('GitHub', 'https://github.com', 0),
            ('Wikipedia', 'https://www.wikipedia.org', 1),
            ('YouTube', 'https://www.youtube.com', 2),
            ('MDN Web Docs', 'https://developer.mozilla.org', 3),
            ('OpenJFX', 'https://openjfx.io', 4),
            ('Google', 'https://www.google.com', 5);
    END IF;
END
$schema$;
CREATE INDEX IF NOT EXISTS speed_dial_position_idx ON speed_dial (position, id);


-- Opt-in page text index. One bounded snapshot per URL; no password forms are indexed.
CREATE TABLE IF NOT EXISTS page_text (
    url VARCHAR(2048) PRIMARY KEY,
    title VARCHAR(512) NOT NULL,
    body TEXT NOT NULL CHECK (length(body) <= 200000),
    captured_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    document TSVECTOR GENERATED ALWAYS AS (to_tsvector('simple', title || ' ' || body)) STORED
);
CREATE INDEX IF NOT EXISTS page_text_document_idx ON page_text USING GIN(document);
