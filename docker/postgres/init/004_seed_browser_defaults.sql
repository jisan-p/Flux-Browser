\set ON_ERROR_STOP on

INSERT INTO flux_browser.settings (setting_key, setting_value)
VALUES
    ('accent', 'RED'),
    ('wallpaper', 'GRID'),
    ('sidebar_visible', 'true'),
    ('panel_docked', 'true'),
    ('reduced_motion', 'false'),
    ('ui_scale', '13.0')
ON CONFLICT (setting_key) DO NOTHING;

INSERT INTO flux_browser.bookmark_folders (folder_id, name, position)
VALUES (1, 'Bookmarks', 0)
ON CONFLICT (folder_id) DO NOTHING;

SELECT setval(
    pg_get_serial_sequence(
        'flux_browser.bookmark_folders',
        'folder_id'
    ),
    GREATEST(
        (SELECT max(folder_id) FROM flux_browser.bookmark_folders),
        1
    )
);

INSERT INTO flux_browser.speed_dial_entries (
    speed_dial_id,
    title,
    url,
    position
)
VALUES
    ('00000000-0000-0000-0000-000000000001', 'YouTube', 'https://youtube.com', 0),
    ('00000000-0000-0000-0000-000000000002', 'Twitch', 'https://twitch.tv', 1),
    ('00000000-0000-0000-0000-000000000003', 'Discord', 'https://discord.com', 2),
    ('00000000-0000-0000-0000-000000000004', 'Reddit', 'https://reddit.com', 3),
    ('00000000-0000-0000-0000-000000000005', 'GitHub', 'https://github.com', 4),
    ('00000000-0000-0000-0000-000000000006', 'Gmail', 'https://mail.google.com', 5)
ON CONFLICT (speed_dial_id) DO NOTHING;

INSERT INTO flux_browser.window_state (singleton)
VALUES (true)
ON CONFLICT (singleton) DO NOTHING;
