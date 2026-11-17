package com.vodiwalker.blackhole;

import android.app.Activity;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

/** A small, offline-first Android shell for the Persian WebGL experience. */
public final class MainActivity extends Activity {
    private static final String APP_HOST = "black-hole.local";
    private static final String APP_ORIGIN = "https://" + APP_HOST;
    private static final int SPACE_COLOR = Color.rgb(3, 5, 11);

    private FrameLayout root;
    private WebView webView;
    private View fullScreenView;
    private WebChromeClient.CustomViewCallback fullScreenCallback;
    private int previousSystemUiVisibility;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setUpSystemBars();

        root = new FrameLayout(this);
        root.setBackgroundColor(SPACE_COLOR);
        webView = new WebView(this);
        webView.setBackgroundColor(SPACE_COLOR);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setSupportZoom(false);
        settings.setTextZoom(100);
        settings.setMediaPlaybackRequiresUserGesture(true);

        webView.setWebViewClient(new OfflineAssetClient());
        webView.setWebChromeClient(new ExperienceChromeClient());
        root.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER));
        setContentView(root);

        if (savedInstanceState != null && webView.restoreState(savedInstanceState) != null) {
            return;
        }
        webView.loadUrl(APP_ORIGIN + "/index.html");
    }

    private void setUpSystemBars() {
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
                | WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        window.setStatusBarColor(SPACE_COLOR);
        window.setNavigationBarColor(SPACE_COLOR);
        window.getDecorView().setSystemUiVisibility(0);
    }

    private boolean isAppUrl(String rawUrl) {
        Uri uri = Uri.parse(rawUrl);
        return "https".equalsIgnoreCase(uri.getScheme())
                && APP_HOST.equalsIgnoreCase(uri.getHost());
    }

    private final class OfflineAssetClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            // This package is intentionally offline and never opens arbitrary web pages.
            return !isAppUrl(request.getUrl().toString());
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return !isAppUrl(url);
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            return serveBundledAsset(request.getUrl());
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
            return serveBundledAsset(Uri.parse(url));
        }
    }

    private WebResourceResponse serveBundledAsset(Uri uri) {
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || !APP_HOST.equalsIgnoreCase(uri.getHost())) {
            return null;
        }

        String path = Uri.decode(uri.getPath() == null ? "/" : uri.getPath());
        String relativePath = path.startsWith("/") ? path.substring(1) : path;
        if (relativePath.isEmpty()) {
            relativePath = "index.html";
        }
        if (relativePath.contains("..") || relativePath.indexOf('\\') >= 0) {
            return errorResponse(403, "Forbidden");
        }

        try {
            InputStream asset = getAssets().open("www/" + relativePath);
            String mimeType = mimeTypeFor(relativePath);
            String encoding = isTextMimeType(mimeType) ? "UTF-8" : null;
            return new WebResourceResponse(mimeType, encoding, asset);
        } catch (IOException notFound) {
            return errorResponse(404, "Not Found");
        }
    }

    private String mimeTypeFor(String path) {
        int dot = path.lastIndexOf('.');
        String extension = dot >= 0 ? path.substring(dot + 1).toLowerCase() : "";
        switch (extension) {
            case "html": return "text/html";
            case "css": return "text/css";
            case "js":
            case "mjs": return "text/javascript";
            case "json":
            case "map": return "application/json";
            case "svg": return "image/svg+xml";
            case "png": return "image/png";
            case "jpg":
            case "jpeg": return "image/jpeg";
            case "webp": return "image/webp";
            case "ico": return "image/x-icon";
            case "woff": return "font/woff";
            case "woff2": return "font/woff2";
            case "ttf": return "font/ttf";
            default: return "application/octet-stream";
        }
    }

    private boolean isTextMimeType(String mimeType) {
        return mimeType.startsWith("text/")
                || mimeType.equals("application/json")
                || mimeType.equals("image/svg+xml");
    }

    private WebResourceResponse errorResponse(int code, String reason) {
        String body = reason + "\n";
        return new WebResourceResponse(
                "text/plain",
                "UTF-8",
                code,
                reason,
                Collections.singletonMap("Cache-Control", "no-store"),
                new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
    }

    private final class ExperienceChromeClient extends WebChromeClient {
        @Override
        public void onShowCustomView(View view, CustomViewCallback callback) {
            if (fullScreenView != null) {
                callback.onCustomViewHidden();
                return;
            }
            fullScreenView = view;
            fullScreenCallback = callback;
            previousSystemUiVisibility = getWindow().getDecorView().getSystemUiVisibility();
            webView.setVisibility(View.GONE);
            root.addView(view, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER));
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }

        @Override
        public void onHideCustomView() {
            hideCustomView();
        }
    }

    private void hideCustomView() {
        if (fullScreenView == null) {
            return;
        }
        root.removeView(fullScreenView);
        fullScreenView = null;
        webView.setVisibility(View.VISIBLE);
        getWindow().getDecorView().setSystemUiVisibility(previousSystemUiVisibility);
        WebChromeClient.CustomViewCallback callback = fullScreenCallback;
        fullScreenCallback = null;
        if (callback != null) {
            callback.onCustomViewHidden();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (webView != null) {
            webView.saveState(outState);
        }
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
        }
    }

    @Override
    protected void onPause() {
        if (webView != null) {
            webView.onPause();
        }
        super.onPause();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (fullScreenView != null) {
            hideCustomView();
        } else if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (fullScreenView != null) {
            hideCustomView();
        }
        if (webView != null) {
            webView.stopLoading();
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
