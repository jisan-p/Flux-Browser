package com.flux.browser.feature;

import java.time.*;

/** Local calendar-day ranges, with an exclusive upper bound (including DST days). */
public enum BrowsingPeriod {
    ALL, TODAY, YESTERDAY, OLDER;
    public record Range(Instant from, Instant until) {
        public boolean contains(Instant value) {
            return (from == null || !value.isBefore(from)) && (until == null || value.isBefore(until));
        }
    }
    public Range range(LocalDate today, ZoneId zone) {
        Instant start = today.atStartOfDay(zone).toInstant();
        Instant yesterday = today.minusDays(1).atStartOfDay(zone).toInstant();
        return switch (this) {
            case ALL -> new Range(null, null);
            case TODAY -> new Range(start, today.plusDays(1).atStartOfDay(zone).toInstant());
            case YESTERDAY -> new Range(yesterday, start);
            case OLDER -> new Range(null, yesterday);
        };
    }
    public Range range() { return range(LocalDate.now(), ZoneId.systemDefault()); }
}
