package com.ffps.ksk;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.provider.Settings;
import android.view.WindowManager;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SettingsActivity extends PreferenceActivity
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        addPreferencesFromResource(R.xml.preferences);
        populateApps((ListPreference) findPreference(Prefs.LAUNCH_APP));

        findPreference(Prefs.SCHEDULE).setOnPreferenceChangeListener(
                (p, v) -> check(Schedule.isValid(str(v)), R.string.toast_bad_schedule));
        findPreference(Prefs.RETRY_COUNT).setOnPreferenceChangeListener(
                (p, v) -> check(inRange(str(v), 0, 9999), R.string.toast_bad_number));
        findPreference(Prefs.RETRY_PAUSE).setOnPreferenceChangeListener(
                (p, v) -> check(inRange(str(v), 1, 86400), R.string.toast_bad_number));
        findPreference(Prefs.LAUNCH_DELAY).setOnPreferenceChangeListener(
                (p, v) -> check(inRange(str(v), 0, 600), R.string.toast_bad_number));
        findPreference(Prefs.OVERLAY).setOnPreferenceClickListener(p -> {
            openOverlaySettings();
            return true;
        });
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
        findPreference(Prefs.LAUNCH_DELAY).setSummary(
                sp.getString(Prefs.LAUNCH_DELAY, String.valueOf(Prefs.DEF_LAUNCH_DELAY)));
        ListPreference app = (ListPreference) findPreference(Prefs.LAUNCH_APP);
        CharSequence appName = app.getEntry();
        app.setSummary(appName == null || app.getValue() == null || app.getValue().length() == 0
                ? getString(R.string.not_set) : appName);
        Preference overlay = findPreference(Prefs.OVERLAY);
        if (Build.VERSION.SDK_INT < 29) {
            overlay.setEnabled(false);
            overlay.setSummary(R.string.overlay_na);
        } else {
            overlay.setSummary(Settings.canDrawOverlays(this) ? R.string.overlay_on : R.string.overlay_off);
        }
    }

    private void populateApps(ListPreference lp) {
        PackageManager pm = getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN);
        main.addCategory(Intent.CATEGORY_LAUNCHER);
        List<String[]> apps = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ResolveInfo ri : pm.queryIntentActivities(main, 0)) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || !seen.add(pkg)) {
                continue;
            }
            apps.add(new String[]{String.valueOf(ri.loadLabel(pm)), pkg});
        }
        Collections.sort(apps, (a, b) -> a[0].compareToIgnoreCase(b[0]));
        CharSequence[] entries = new CharSequence[apps.size() + 1];
        CharSequence[] values = new CharSequence[apps.size() + 1];
        entries[0] = getString(R.string.not_set);
        values[0] = "";
        for (int i = 0; i < apps.size(); i++) {
            entries[i + 1] = apps.get(i)[0];
            values[i + 1] = apps.get(i)[1];
        }
        lp.setEntries(entries);
        lp.setEntryValues(values);
    }

    private void openOverlaySettings() {
        if (Build.VERSION.SDK_INT < 23) {
            return;
        }
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.overlay_open_failed, Toast.LENGTH_LONG).show();
        }
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
