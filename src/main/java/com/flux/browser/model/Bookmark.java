package com.flux.browser.model;

import java.time.Instant;

public record Bookmark(long id, String title, String url, Instant createdAt, String category) {}
