\set ON_ERROR_STOP on

-- All Flux Browser database DDL belongs in numbered SQL files in this directory.
-- The PostgreSQL Docker entrypoint executes them in lexical order for a new volume.
CREATE SCHEMA IF NOT EXISTS flux_browser AUTHORIZATION postgres;

COMMENT ON SCHEMA flux_browser IS
    'Persistent application data for Flux Browser.';
