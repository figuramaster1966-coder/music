package com.figuramaster.atlas;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

/**
 * FIGURAMASTER: Android window onto the published, automatically updated music atlas.
 * No credentials, no JavaScript bridge, and no assets copied from the site.
 */
public final class MainActivity extends Activity {
    private static final String HOME_URL = "https://figuramaster1966-coder.github.io/music/";
    private static final String TRUSTED_HOST = "figuramaster1966-coder.github.io";
    private static final String TRUSTED_PATH_PREFIX = "/music/";

    private WebView webView;
    private ProgressBar progress;
    private LinearLayout offlinePanel;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);

        View root = findViewById(R.id.root);
        // Android 15+ enforces edge-to-edge when targeting API 35.
        if (Build.VERSION.SDK_INT >= 35) {
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return insets;
            });
            root.requestApplyInsets();
        }

        webView = findViewById(R.id.atlas_view);
        progress = findViewById(R.id.loading_progress);
        offlinePanel = findViewById(R.id.offline_panel);
        findViewById(R.id.retry_button).setOnClickListener(v -> loadFreshHome());
        findViewById(R.id.browser_button).setOnClickListener(v -> openExternal(Uri.parse(HOME_URL)));

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);  // The existing audio player needs JS.
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);

        webView.setBackgroundColor(Color.rgb(10, 25, 20));
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (!request.isForMainFrame()) return false;
                Uri uri = request.getUrl();
                if (isInternal(uri)) return false;
                openExternal(uri);
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                progress.setVisibility(View.VISIBLE);
                progress.setProgress(8);
                offlinePanel.setVisibility(View.GONE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progress.setVisibility(View.GONE);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request,
                                        WebResourceError error) {
                if (request.isForMainFrame()) showOffline();
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request,
                                            android.webkit.WebResourceResponse errorResponse) {
                if (request.isForMainFrame() && errorResponse.getStatusCode() >= 400) showOffline();
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progress.setProgress(newProgress);
            }

            // The atlas has VK links with target=_blank; open those in the browser.
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog,
                                          boolean isUserGesture, Message resultMsg) {
                if (!isUserGesture) return false;
                final WebView popup = new WebView(MainActivity.this);
                popup.setWebViewClient(new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest request) {
                        openExternal(request.getUrl());
                        v.post(v::destroy);
                        return true;
                    }
                });
                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(popup);
                resultMsg.sendToTarget();
                return true;
            }
        });
        loadFreshHome();
    }

    private boolean isInternal(Uri uri) {
        return "https".equalsIgnoreCase(uri.getScheme())
                && TRUSTED_HOST.equalsIgnoreCase(uri.getHost())
                && uri.getPath() != null
                && uri.getPath().startsWith(TRUSTED_PATH_PREFIX);
    }

    private void loadFreshHome() {
        offlinePanel.setVisibility(View.GONE);
        // New query on each launch forces the HTML to refresh, while MP3 streams
        // and original artwork URLs remain unchanged and can benefit from HTTP cache.
        webView.loadUrl(HOME_URL + "?app_refresh=" + System.currentTimeMillis());
    }

    private void showOffline() {
        offlinePanel.setVisibility(View.VISIBLE);
        progress.setVisibility(View.GONE);
    }

    private void openExternal(Uri uri) {
        String scheme = uri.getScheme();
        if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) return;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException error) {
            Toast.makeText(this, "Нет приложения для открытия ссылки", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBackPressed() {
        if (offlinePanel.getVisibility() == View.VISIBLE) {
            loadFreshHome();
        } else if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            ((ViewGroup) webView.getParent()).removeView(webView);
            webView.stopLoading();
            webView.destroy();
        }
        super.onDestroy();
    }
}
