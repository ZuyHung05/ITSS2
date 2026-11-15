package com.example.demo;

import java.time.*;
import java.util.concurrent.atomic.AtomicReference;

public class MutableClock extends Clock {
    private final AtomicReference<Instant> current=new AtomicReference<>(Instant.parse("2026-11-13T08:30:00Z"));
    public void set(String instant) { current.set(Instant.parse(instant)); }
    @Override public ZoneId getZone() { return ZoneOffset.UTC; }
    @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant(),zone); }
    @Override public Instant instant() { return current.get(); }
}
