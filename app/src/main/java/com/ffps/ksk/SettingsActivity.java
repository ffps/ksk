package com.ffps.ksk;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.view.WindowManager;
import android.widget.Toast;

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
        String url = sp.getString(Prefs.URL, "");
        findPreference(Prefs.URL).setSummary(url.length() == 0 ? getString(R.string.not_set) : url);
        findPreference(Prefs.RETRY_COUNT).setSummary(sp.getString(Prefs.RETRY_COUNT, "30"));
        findPreference(Prefs.RETRY_PAUSE).setSummary(sp.getString(Prefs.RETRY_PAUSE, "10"));
        String sch = sp.getString(Prefs.SCHEDULE, "").trim();
        findPreference(Prefs.SCHEDULE).setSummary(sch.length() == 0 ? getString(R.string.schedule_off) : sch);
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
