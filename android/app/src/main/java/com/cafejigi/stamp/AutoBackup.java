package com.cafejigi.stamp;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Calendar;

/** 매일 23시 자동 백업: 다운로드/카페지기 폴더에 백업 파일 저장 */
final class AutoBackup {
    static final String APP_URL = "https://sugar5675-web.github.io/weatherlog24/stamp/";
    static final String FOLDER = "카페지기";
    static final int HOUR = 23;
    static final String ACTION = "com.cafejigi.stamp.AUTO_BACKUP";
    private static final String PREFS = "autobackup";
    private static final String KEY_LAST = "last";

    private AutoBackup() {
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** 처음 실행 시 기준 시각 기록 (설치 직후 빈 백업 방지) */
    static void init(Context c) {
        if (!prefs(c).contains(KEY_LAST)) prefs(c).edit().putLong(KEY_LAST, System.currentTimeMillis()).apply();
    }

    /** 가장 최근의 23:00 (지금 이전) */
    static long lastSlot(long now) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(now);
        cal.set(Calendar.HOUR_OF_DAY, HOUR);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() > now) cal.add(Calendar.DAY_OF_MONTH, -1);
        return cal.getTimeInMillis();
    }

    static boolean isDue(Context c) {
        long now = System.currentTimeMillis();
        long last = prefs(c).getLong(KEY_LAST, now);
        return last < lastSlot(now);
    }

    static boolean saveAuto(Context c, String name, String text) {
        boolean ok = false;
        try {
            ok = saveToDownloads(c, name, text);
        } catch (Exception ignored) {
        }
        if (ok) prefs(c).edit().putLong(KEY_LAST, System.currentTimeMillis()).apply();
        return ok;
    }

    static String safeName(String name) {
        String s = name.replaceAll("[\\\\/:*?\"<>|]", "_");
        return s.endsWith(".json") ? s : s + ".json";
    }

    static boolean saveToDownloads(Context c, String name, String text) throws Exception {
        byte[] bytes = text.getBytes(Charset.forName("UTF-8"));
        if (Build.VERSION.SDK_INT >= 29) {
            ContentResolver r = c.getContentResolver();
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            cv.put(MediaStore.MediaColumns.MIME_TYPE, "application/json");
            cv.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER);
            Uri u = r.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
            if (u == null) return false;
            OutputStream os = r.openOutputStream(u);
            if (os == null) return false;
            os.write(bytes);
            os.close();
            return true;
        }
        if (Build.VERSION.SDK_INT >= 23
                && c.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), FOLDER);
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        FileOutputStream fos = new FileOutputStream(new File(dir, name));
        fos.write(bytes);
        fos.close();
        return true;
    }

    /** 다음 23:00에 알람 예약 */
    static void schedule(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Intent i = new Intent(c, BackupReceiver.class).setAction(ACTION);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getBroadcast(c, 23, i, flags);
        long next = lastSlot(System.currentTimeMillis()) + 24L * 60 * 60 * 1000;
        am.setWindow(AlarmManager.RTC_WAKEUP, next, 10L * 60 * 1000, pi);
    }
}
