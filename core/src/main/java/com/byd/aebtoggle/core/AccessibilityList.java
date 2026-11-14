package com.byd.aebtoggle.core;

/**
 * Pure logic of the H07 fix: how our accessibility service is added to the value of the system
 * setting "enabled_accessibility_services" without damaging the other entries.
 *
 * The value is a ':' separated list of component names ("pkg/Class"), or empty, or the literal
 * "null". It is only ever rewritten after a COMPLETE, well-formed read; the caller (AdbHelper)
 * must not write anything when withComponent() returns null.
 */
public final class AccessibilityList {
    private static final String SAFE = "[A-Za-z0-9_.$/:\\-]*";

    private AccessibilityList() {
    }

    /** True for an empty list, the literal "null", or a colon separated list of component names. */
    public static boolean isSafe(String list) {
        return list != null && list.matches(SAFE);
    }

    /** True when the list has no entries (empty text or the literal "null"). */
    public static boolean isEmpty(String list) {
        return list == null || list.isEmpty() || list.equals("null");
    }

    /** Exact match of one ':' separated entry (a longer name that merely contains it does not count). */
    public static boolean contains(String list, String component) {
        if (isEmpty(list)) return false;
        for (String entry : list.split(":")) {
            if (entry.equals(component)) return true;
        }
        return false;
    }

    /**
     * The value to write: the list unchanged when the component is already there, the component
     * alone for an empty list, otherwise list + ":" + component.
     * Returns null when list or component is not safe, meaning: do NOT write anything.
     */
    public static String withComponent(String list, String component) {
        if (list == null || component == null) return null;
        String current = list.trim();
        if (!isSafe(current) || !isSafe(component) || component.isEmpty()) return null;
        if (isEmpty(current)) return component;
        if (contains(current, component)) return current;
        return current + ":" + component;
    }
}
