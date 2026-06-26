package com.ecommerce.common.test;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class SystemClockService {
    private static final AtomicLong offsetMinutes = new AtomicLong(0);
    private static final AtomicReference<LocalDateTime> fixedTime = new AtomicReference<>(null);
    private SystemClockService() {}
    public static LocalDateTime now() {
        LocalDateTime fixed = fixedTime.get();
        if (fixed != null) return fixed;
        return LocalDateTime.now().plusMinutes(offsetMinutes.get());
    }
    public static void setOffset(long minutes) { fixedTime.set(null); offsetMinutes.set(minutes); }
    public static void setFixed(LocalDateTime time) { offsetMinutes.set(0); fixedTime.set(time); }
    public static void reset() { offsetMinutes.set(0); fixedTime.set(null); }
    public static long getOffsetMinutes() { return offsetMinutes.get(); }
    public static LocalDateTime getFixedTime() { return fixedTime.get(); }
}
