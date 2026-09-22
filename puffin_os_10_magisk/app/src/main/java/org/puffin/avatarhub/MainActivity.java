package org.puffin.avatarhub;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

public class MainActivity extends Activity {

    private WebView cloudWebView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout container = new FrameLayout(this);
        container.setBackgroundColor(0xFF121212);

        cloudWebView = new WebView(this);
        cloudWebView.setBackgroundColor(0xFF121212);

        WebSettings settings = cloudWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);

        // Enable hardware acceleration rendering for Puffin Cloud Avatar streaming
        cloudWebView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        cloudWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);
                return true;
            }
        });

        cloudWebView.setWebChromeClient(new WebChromeClient());

        // Load Puffin Cloud Service Avatar Portal
        cloudWebView.loadUrl("https://www.puffin.com");

        container.addView(cloudWebView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        setContentView(container);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && cloudWebView != null && cloudWebView.canGoBack()) {
            cloudWebView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
