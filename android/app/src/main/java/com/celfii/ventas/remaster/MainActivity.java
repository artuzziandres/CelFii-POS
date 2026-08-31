package com.celfii.ventas.remaster;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;

public final class MainActivity extends Activity {
    private WebView web;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (BuildConfig.CELFII_WEB_URL.isBlank()) {
            TextView error = new TextView(this);
            error.setText("Falta CELFII_WEB_URL. Compilá la APK desde el workflow con la URL del despliegue de Apps Script.");
            error.setTextColor(Color.WHITE); error.setBackgroundColor(Color.rgb(8,11,9)); error.setTextSize(18); error.setPadding(40,80,40,40);
            setContentView(error); return;
        }
        web = new WebView(this); web.setBackgroundColor(Color.rgb(8,11,9)); setContentView(web);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setMediaPlaybackRequiresUserGesture(false);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !request.getUrl().toString().startsWith("https://script.google.com/") && !request.getUrl().toString().startsWith("https://script.googleusercontent.com/");
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onPermissionRequest(PermissionRequest request) {
                if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) request.grant(request.getResources());
                else requestPermissions(new String[]{Manifest.permission.CAMERA}, 40);
            }
        });
        web.loadUrl(BuildConfig.CELFII_WEB_URL);
    }
    @Override public void onBackPressed() { if (web != null && web.canGoBack()) web.goBack(); else super.onBackPressed(); }
}
