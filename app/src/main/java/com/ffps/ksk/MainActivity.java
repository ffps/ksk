package com.ffps.ksk;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

public class MainActivity extends Activity {
    private static final long TICK_MS = 10000;

    private final Handler handler = new Handler();
    private SharedPreferences prefs;
    private FrameLayout root;
    private WebView web;

    // настройки
    private String url = "";
    private String schedule = "";
    private int maxRetries = 30;
    private int retryPause = 10;
    private boolean ignoreSsl;

    // состояние загрузки
    private boolean started;
    private boolean autoOpenedSettings;
    private int attempt;
    private int gen;
    private boolean loadFailed;
    private boolean showingError;
    private boolean retryPending;

    // расписание
    private long nextReload = -1;
    private long lastTick;

    // скрытый вход в настройки
    private int cornerTaps;
    private long cornerFirstTap;

    private final Runnable retryRunnable = () -> {
        retryPending = false;
        loadPage();
    };

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            long now = System.currentTimeMillis();
            if (Math.abs(now - lastTick) > 120000) {
                // системные часы скакнули (например, синхронизация по сети после загрузки)
                nextReload = Schedule.next(schedule, now);
            }
            lastTick = now;
            if (nextReload > 0 && now >= nextReload) {
                nextReload = Schedule.next(schedule, now);
                reloadNow();
            }
            handler.postDelayed(this, TICK_MS);
        }
    };

    private final Runnable hideRunnable = this::hideSystemUi;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        web = new WebView(this);
        web.setBackgroundColor(Color.BLACK);
        setupWebView();
        root.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // невидимая зона в левом верхнем углу: 5 нажатий подряд открывают настройки
        View corner = new View(this);
        int size = (int) (56 * getResources().getDisplayMetrics().density);
        root.addView(corner, new FrameLayout.LayoutParams(size, size, Gravity.TOP | Gravity.LEFT));
        corner.setOnClickListener(v -> onCornerTap());

        setContentView(root);

        root.setOnSystemUiVisibilityChangeListener(visibility -> {
            if ((visibility & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0) {
                handler.removeCallbacks(hideRunnable);
                handler.postDelayed(hideRunnable, 2000);
            }
        });
    }

    private void setupWebView() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        if (Build.VERSION.SDK_INT >= 21) {
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
        web.setLongClickable(false);
        web.setOnLongClickListener(v -> true);
        web.setHapticFeedbackEnabled(false);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String target) {
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String finishedUrl) {
                if (finishedUrl == null || finishedUrl.startsWith("about:") || finishedUrl.startsWith("data:")) {
                    return;
                }
                // успех засчитываем с небольшой задержкой: на старых WebView порядок
                // onReceivedError / onPageFinished не гарантирован
                final int g = gen;
                handler.postDelayed(() -> {
                    if (g == gen && !loadFailed && !showingError) {
                        attempt = 0;
                    }
                }, 1000);
            }

            @SuppressWarnings("deprecation")
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                onLoadFailed();
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler h, SslError error) {
                if (ignoreSsl) {
                    h.proceed();
                } else {
                    h.cancel();
                    onLoadFailed();
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();

        String oldUrl = url;
        readSettings();

        if (url.length() == 0) {
            showPage(getString(R.string.err_no_url_title), getString(R.string.err_no_url));
            if (!autoOpenedSettings) {
                autoOpenedSettings = true;
                openSettings();
            }
        } else if (!started || !url.equals(oldUrl) || (showingError && !retryPending)) {
            started = true;
            reloadNow();
        }

        long now = System.currentTimeMillis();
        lastTick = now;
        nextReload = Schedule.next(schedule, now);
        handler.removeCallbacks(ticker);
        handler.postDelayed(ticker, TICK_MS);

        hideSystemUi();
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(ticker);
        handler.removeCallbacks(retryRunnable);
        retryPending = false;
        web.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        root.removeView(web);
        web.destroy();
        super.onDestroy();
    }

    private void readSettings() {
        String u = prefs.getString(Prefs.URL, "").trim();
        if (u.length() > 0 && !u.contains("://")) {
            u = "http://" + u;
        }
        url = u;
        schedule = prefs.getString(Prefs.SCHEDULE, "").trim();
        maxRetries = Math.max(0, Prefs.getInt(prefs, Prefs.RETRY_COUNT, 30));
        retryPause = Math.max(1, Prefs.getInt(prefs, Prefs.RETRY_PAUSE, 10));
        ignoreSsl = prefs.getBoolean(Prefs.IGNORE_SSL, false);
    }

    private void reloadNow() {
        handler.removeCallbacks(retryRunnable);
        retryPending = false;
        attempt = 0;
        loadPage();
    }

    private void loadPage() {
        gen++;
        loadFailed = false;
        showingError = false;
        web.loadUrl(url);
    }

    private void onLoadFailed() {
        if (retryPending || showingError) {
            return;
        }
        loadFailed = true;
        attempt++;
        final String title = getString(R.string.err_title);
        if (maxRetries == 0 || attempt <= maxRetries) {
            retryPending = true;
            final String text = url + "\n" + getString(R.string.err_retry,
                    attempt, maxRetries == 0 ? "∞" : String.valueOf(maxRetries), retryPause);
            handler.post(() -> showPage(title, text));
            handler.postDelayed(retryRunnable, retryPause * 1000L);
        } else {
            final String text = getString(R.string.err_giveup, maxRetries);
            handler.post(() -> showPage(title, text));
        }
    }

    private void showPage(String title, String text) {
        showingError = true;
        String html = "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'></head>"
                + "<body style='background:#000;color:#ddd;font-family:sans-serif;text-align:center;padding:20% 6% 0'>"
                + "<h2>" + TextUtils.htmlEncode(title) + "</h2>"
                + "<p>" + TextUtils.htmlEncode(text).replace("\n", "<br>") + "</p></body></html>";
        web.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
    }

    private void onCornerTap() {
        long now = SystemClock.uptimeMillis();
        if (cornerTaps == 0 || now - cornerFirstTap > 4000) {
            cornerTaps = 0;
            cornerFirstTap = now;
        }
        if (++cornerTaps >= 5) {
            cornerTaps = 0;
            openSettings();
        }
    }

    private void openSettings() {
        startActivity(new Intent(this, SettingsActivity.class));
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            openSettings();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (!showingError && web.canGoBack()) {
                web.goBack();
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUi();
        }
    }

    private void hideSystemUi() {
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN;
        if (Build.VERSION.SDK_INT >= 19) {
            flags |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        }
        root.setSystemUiVisibility(flags);
    }
}
