package com.ffps.ksk;

import android.content.SharedPreferences;

final class Prefs {
    static final String URL = "url";
    static final String RETRY_COUNT = "retry_count";
    static final String RETRY_PAUSE = "retry_pause";
    static final String SCHEDULE = "schedule";
    static final String AUTOSTART = "autostart";
    static final String IGNORE_SSL = "ignore_ssl";

    static final String DEF_SCHEDULE = "59 * * * *";
    static final int DEF_RETRY_COUNT = 0;
    static final int DEF_RETRY_PAUSE = 10;

    private Prefs() {
    }

    static int getInt(SharedPreferences p, String key, int def) {
        try {
            return Integer.parseInt(p.getString(key, String.valueOf(def)).trim());
        } catch (Exception e) {
            return def;
        }
    }
}
