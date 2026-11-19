package com.cafejigi.stamp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;

/** 23시 알람 또는 재부팅 시 실행. 화면 없이 적립 페이지를 열어 백업 파일을 저장함 */
public class BackupReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        final Context app = context.getApplicationContext();
        AutoBackup.init(app);
        AutoBackup.schedule(app);
        if (!AutoBackup.ACTION.equals(intent.getAction())) return;
        if (!AutoBackup.isDue(app)) return;

        final PendingResult pr = goAsync();
        final Handler h = new Handler(Looper.getMainLooper());
        h.post(new Runnable() {
            @Override
            public void run() {
                final WebView[] holder = new WebView[1];
                final boolean[] done = new boolean[1];
                final Runnable finish = new Runnable() {
                    @Override
                    public void run() {
                        if (done[0]) return;
                        done[0] = true;
                        try {
                            if (holder[0] != null) holder[0].destroy();
                        } catch (Exception ignored) {
                        }
                        pr.finish();
                    }
                };
                try {
                    WebView w = new WebView(app);
                    holder[0] = w;
                    WebSettings s = w.getSettings();
                    s.setJavaScriptEnabled(true);
                    s.setDomStorageEnabled(true);
                    s.setDatabaseEnabled(true);
                    w.addJavascriptInterface(new HeadlessBridge(app, h, finish), "CafeApp");
                    w.loadUrl(AutoBackup.APP_URL + "?autobackup=1");
                } catch (Exception e) {
                    finish.run();
                    return;
                }
                h.postDelayed(finish, 50000);
            }
        });
    }

    public static class HeadlessBridge {
        private final Context app;
        private final Handler h;
        private final Runnable finish;

        HeadlessBridge(Context app, Handler h, Runnable finish) {
            this.app = app;
            this.h = h;
            this.finish = finish;
        }

        @JavascriptInterface
        public boolean isApp() {
            return true;
        }

        @JavascriptInterface
        public boolean autoBackupDue() {
            return AutoBackup.isDue(app);
        }

        @JavascriptInterface
        public boolean autoBackup(String name, String text) {
            boolean ok = AutoBackup.saveAuto(app, AutoBackup.safeName(name), text);
            h.post(finish);
            return ok;
        }
    }
}
