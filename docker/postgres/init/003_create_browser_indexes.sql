\set ON_ERROR_STOP on

CREATE INDEX IF NOT EXISTS bookmarks_search_idx
    ON flux_browser.bookmarks (lower(title));

CREATE INDEX IF NOT EXISTS bookmarks_created_idx
    ON flux_browser.bookmarks (created_at DESC);

CREATE INDEX IF NOT EXISTS visits_recent_idx
    ON flux_browser.visits (visited_at DESC);

CREATE INDEX IF NOT EXISTS visits_url_idx
    ON flux_browser.visits (url);

CREATE INDEX IF NOT EXISTS downloads_recent_idx
    ON flux_browser.downloads (started_at DESC);

CREATE INDEX IF NOT EXISTS session_tabs_session_position_idx
    ON flux_browser.session_tabs (session_id, position);

CREATE INDEX IF NOT EXISTS recently_closed_tabs_recent_idx
    ON flux_browser.recently_closed_tabs (closed_at DESC);
