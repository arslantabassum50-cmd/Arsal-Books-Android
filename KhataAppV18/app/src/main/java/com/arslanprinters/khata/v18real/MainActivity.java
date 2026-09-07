package com.arslanprinters.khata.v18real;

import android.app.Activity;
import android.content.ClipData;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.util.Base64;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {
    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(Color.parseColor("#F44322"));
        getWindow().setNavigationBarColor(Color.WHITE);
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }

        webView = new WebView(this);
        setContentView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setDatabaseEnabled(true);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setTextZoom(100);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(false);
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        }

        webView.addJavascriptInterface(new AndroidBridge(this), "AndroidBridge");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleExternal(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleExternal(Uri.parse(url));
            }
        });

        webView.loadUrl("file:///android_asset/index.html");
    }

    private boolean handleExternal(Uri uri) {
        if (uri == null) return false;
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
        boolean external = scheme.equals("tel") || scheme.equals("sms") || scheme.equals("mailto") ||
                scheme.equals("whatsapp") || host.equals("wa.me") || host.contains("whatsapp.com");
        if (!external) return false;
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, uri);
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "Required app not found", Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    public class AndroidBridge {
        private final Context context;

        AndroidBridge(Context context) {
            this.context = context;
        }

        private byte[] decodeDataUrl(String dataUrl) {
            if (dataUrl == null) return null;
            int comma = dataUrl.indexOf(',');
            String raw = comma >= 0 ? dataUrl.substring(comma + 1) : dataUrl;
            return Base64.decode(raw, Base64.DEFAULT);
        }

        private String safeName(String name) {
            if (name == null || name.trim().isEmpty()) return "Arslan_Khata.jpg";
            return name.replaceAll("[^A-Za-z0-9._-]", "_");
        }

        @JavascriptInterface
        public void saveJpg(String dataUrl, String fileName) {
            byte[] bytes = decodeDataUrl(dataUrl);
            if (bytes == null) return;
            String name = safeName(fileName);
            try {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Arslan Khata");
                    values.put(MediaStore.Images.Media.IS_PENDING, 1);
                }
                Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri == null) throw new Exception("Unable to create image");
                try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                    if (out != null) out.write(bytes);
                }
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    values.clear();
                    values.put(MediaStore.Images.Media.IS_PENDING, 0);
                    getContentResolver().update(uri, values, null, null);
                }
                runOnUiThread(() -> Toast.makeText(context, "JPG saved", Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(context, "JPG save failed", Toast.LENGTH_SHORT).show());
            }
        }

        @JavascriptInterface
        public void shareJpg(String dataUrl, String fileName, String text) {
            byte[] bytes = decodeDataUrl(dataUrl);
            if (bytes == null) return;
            try {
                File dir = new File(getCacheDir(), "shared");
                if (!dir.exists()) dir.mkdirs();
                File f = new File(dir, safeName(fileName));
                try (FileOutputStream out = new FileOutputStream(f)) {
                    out.write(bytes);
                }
                Uri uri = FileProvider.getUriForFile(context, getPackageName() + ".fileprovider", f);
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("image/jpeg");
                send.putExtra(Intent.EXTRA_STREAM, uri);
                if (text != null && !text.trim().isEmpty()) send.putExtra(Intent.EXTRA_TEXT, text);
                send.setClipData(ClipData.newRawUri("Reminder", uri));
                send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(send, "Share reminder"));
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(context, "Share failed", Toast.LENGTH_SHORT).show());
            }
        }

        @JavascriptInterface
        public void printPage() {
            runOnUiThread(() -> {
                try {
                    PrintManager printManager = (PrintManager) getSystemService(Context.PRINT_SERVICE);
                    String jobName = "Arslan Khata PDF";
                    PrintDocumentAdapter adapter = webView.createPrintDocumentAdapter(jobName);
                    PrintAttributes attrs = new PrintAttributes.Builder()
                            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                            .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                            .build();
                    printManager.print(jobName, adapter, attrs);
                } catch (Exception e) {
                    Toast.makeText(context, "PDF/Print failed", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }
}
