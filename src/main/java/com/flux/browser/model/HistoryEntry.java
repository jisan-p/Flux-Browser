package com.flux.browser.model;

import java.time.Instant;

public record HistoryEntry(long id, String title, String url, Instant visitedAt) {}
