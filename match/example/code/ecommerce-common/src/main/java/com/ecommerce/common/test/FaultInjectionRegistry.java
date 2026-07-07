package com.ecommerce.common.test;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class FaultInjectionRegistry {
    private static final Set<String> faults = ConcurrentHashMap.newKeySet();
    private FaultInjectionRegistry() {}
    public static void add(String name) { faults.add(name); }
    public static void remove(String name) { faults.remove(name); }
    public static void clear() { faults.clear(); }
    public static boolean isActive(String name) { return faults.contains(name); }
    public static Set<String> getAll() { return Collections.unmodifiableSet(faults); }
}
