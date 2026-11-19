package com.cafejigi.stamp;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;

public class MainActivity extends Activity {

    private static final String APP_URL = "https://sugar5675-web.github.io/weatherlog24/stamp/";
    private static final String APP_HOST = "sugar5675-web.github.io";
    private static final String FOLDER = "카페지기";
    private static final int REQ_FILE = 1;
    private static final int REQ_PERM = 2;

    private WebView web;
    private ValueCallback<Uri[]> fileCallback;
    private String pendingName;
    private String pendingText;
    private boolean permAsked;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setTextZoom(100);
        s.setSupportZoom(false);
        s.setUserAgentString(s.getUserAgentString() + " CafejigiApp/1");

        web.addJavascriptInterface(new Bridge(), "CafeApp");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                Uri u = Uri.parse(url);
                if (APP_HOST.equals(u.getHost())) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u));
                } catch (Exception ignored) {
                }
                return true;
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                if (failingUrl != null && failingUrl.startsWith(APP_URL)) showOffline();
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                try {
                    startActivityForResult(Intent.createChooser(i, "백업 파일 선택"), REQ_FILE);
                } catch (ActivityNotFoundException e) {
                    fileCallback = null;
                    return false;
                }
                return true;
            }
        });

        if (savedInstanceState != null) web.restoreState(savedInstanceState);
        else web.loadUrl(APP_URL);
        hideBars();
    }

    private void showOffline() {
        String html = "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'></head>"
                + "<body style='font-family:sans-serif;background:#FBF6EE;color:#3b2f25;text-align:center;padding-top:18vh'>"
                + "<h2>인터넷 연결을 확인해 주세요</h2>"
                + "<p>처음 한 번은 인터넷이 연결된 상태에서 열어야 해요.</p>"
                + "<button style='font-size:20px;padding:14px 28px;border-radius:14px;border:0;background:#6b4f3a;color:#fff' "
                + "onclick=\"location.href='" + APP_URL + "'\">다시 시도</button></body></html>";
        web.loadDataWithBaseURL(APP_URL, html, "text/html", "UTF-8", null);
    }

    private void hideBars() {
        if (Build.VERSION.SDK_INT >= 19) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideBars();
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        // 카운터에서 실수로 앱이 닫히지 않도록 뒤로가기로는 종료하지 않음
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (web != null) web.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.onResume();
        hideBars();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (web != null) web.saveState(out);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_FILE && fileCallback != null) {
            Uri[] result = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            fileCallback.onReceiveValue(result);
            fileCallback = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERM && pendingName != null) {
            String n = pendingName, t = pendingText;
            pendingName = null;
            pendingText = null;
            saveBackup(n, t);
        }
    }

    /** 다운로드/카페지기 폴더에 저장하고, 카톡·메일 등으로 보낼 수 있는 공유 창을 띄움 */
    private void saveBackup(String name, String text) {
        String safe = name.replaceAll("[\\\\/:*?\"<>|]", "_");
        if (!safe.endsWith(".json")) safe += ".json";

        if (Build.VERSION.SDK_INT >= 23 && Build.VERSION.SDK_INT < 29
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                && !permAsked) {
            permAsked = true;
            pendingName = name;
            pendingText = text;
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_PERM);
            return;
        }

        boolean saved = false;
        try {
            saved = saveToDownloads(safe, text);
        } catch (Exception ignored) {
        }

        Toast.makeText(this, saved
                ? "다운로드 > " + FOLDER + " 폴더에 백업을 저장했어요."
                : "백업 파일을 만들었어요. 보낼 곳을 골라 주세요.", Toast.LENGTH_LONG).show();

        try {
            File dir = new File(getCacheDir(), "backup");
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            File f = new File(dir, safe);
            FileOutputStream fos = new FileOutputStream(f);
            fos.write(text.getBytes(Charset.forName("UTF-8")));
            fos.close();
            Uri u = FileProvider.getUriForFile(this, "com.cafejigi.stamp.files", f);
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("application/json");
            i.putExtra(Intent.EXTRA_STREAM, u);
            i.putExtra(Intent.EXTRA_SUBJECT, safe);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(i, "백업 파일 보내기 (카톡 나와의 채팅·메일 등)"));
        } catch (Exception e) {
            if (!saved) Toast.makeText(this, "백업 저장에 실패했어요.", Toast.LENGTH_LONG).show();
        }
    }

    private boolean saveToDownloads(String name, String text) throws Exception {
        byte[] bytes = text.getBytes(Charset.forName("UTF-8"));
        if (Build.VERSION.SDK_INT >= 29) {
            ContentResolver r = getContentResolver();
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
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
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

    private class Bridge {
        @JavascriptInterface
        public boolean isApp() {
            return true;
        }

        @JavascriptInterface
        public void saveBackup(final String name, final String text) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    MainActivity.this.saveBackup(name, text);
                }
            });
        }
    }
}
