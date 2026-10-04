package com.ffps.ksk;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.view.WindowManager;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.Date;

public class SettingsActivity extends PreferenceActivity
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        addPreferencesFromResource(R.xml.preferences);

        findPreference(Prefs.SCHEDULE).setOnPreferenceChangeListener(
                (p, v) -> check(Schedule.isValid(str(v)), R.string.toast_bad_schedule));
        findPreference(Prefs.RETRY_COUNT).setOnPreferenceChangeListener(
                (p, v) -> check(inRange(str(v), 0, 9999), R.string.toast_bad_number));
        findPreference(Prefs.RETRY_PAUSE).setOnPreferenceChangeListener(
                (p, v) -> check(inRange(str(v), 1, 86400), R.string.toast_bad_number));
    }

    @Override
    protected void onResume() {
        super.onResume();
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        refreshSummaries();
    }

    @Override
    protected void onPause() {
        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
        refreshSummaries();
    }

    private void refreshSummaries() {
        SharedPreferences sp = getPreferenceScreen().getSharedPreferences();
        String url = sp.getString(Prefs.URL, "").trim();
        boolean noUrl = url.length() == 0 || url.equals("http://") || url.equals("https://");
        findPreference(Prefs.URL).setSummary(noUrl ? getString(R.string.not_set) : url);
        String count = sp.getString(Prefs.RETRY_COUNT, String.valueOf(Prefs.DEF_RETRY_COUNT)).trim();
        findPreference(Prefs.RETRY_COUNT).setSummary(
                count.equals("0") ? getString(R.string.retry_unlimited) : count);
        findPreference(Prefs.RETRY_PAUSE).setSummary(
                sp.getString(Prefs.RETRY_PAUSE, String.valueOf(Prefs.DEF_RETRY_PAUSE)));
        String sch = sp.getString(Prefs.SCHEDULE, Prefs.DEF_SCHEDULE).trim();
        findPreference(Prefs.SCHEDULE).setSummary(sch.length() == 0 ? getString(R.string.schedule_off) : sch);
        findPreference(Prefs.AUTOSTART).setSummary(bootSummary(sp.getString(Prefs.LAST_BOOT, "")));
    }

    private String bootSummary(String raw) {
        String[] parts = raw.split("\\|", 3);
        if (parts.length < 3) {
            return getString(R.string.boot_never);
        }
        try {
            String when = DateFormat.getDateTimeInstance().format(new Date(Long.parseLong(parts[0])));
            String action = parts[1].substring(parts[1].lastIndexOf('.') + 1);
            return getString(R.string.boot_last, when, action, parts[2]);
        } catch (RuntimeException e) {
            return getString(R.string.boot_never);
        }
    }

    private static String str(Object v) {
        return v == null ? "" : v.toString().trim();
    }

    private static boolean inRange(String s, int lo, int hi) {
        try {
            int n = Integer.parseInt(s);
            return n >= lo && n <= hi;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean check(boolean ok, int errorRes) {
        if (!ok) {
            Toast.makeText(this, errorRes, Toast.LENGTH_LONG).show();
        }
        return ok;
    }
}
