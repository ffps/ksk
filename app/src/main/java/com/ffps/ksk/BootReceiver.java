package com.ffps.ksk;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(context);
        String action = intent == null ? "?" : String.valueOf(intent.getAction());
        String result;
        if (!p.getBoolean(Prefs.AUTOSTART, true)) {
            result = "off";
        } else {
            try {
                Intent i = new Intent(context, MainActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(i);
                result = "ok";
            } catch (Exception e) {
                result = "error " + e.getClass().getSimpleName();
            }
        }
        // след для диагностики: виден в настройках под галочкой автозапуска
        p.edit().putString(Prefs.LAST_BOOT, System.currentTimeMillis() + "|" + action + "|" + result).commit();
    }
}
