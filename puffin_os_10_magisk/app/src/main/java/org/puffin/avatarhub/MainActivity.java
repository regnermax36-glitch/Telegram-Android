package org.puffin.avatarhub;

import android.app.Activity;
import android.os.Bundle;
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
        cloudWebView = new WebView(this);
        container.addView(cloudWebView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        setContentView(container);

        WebSettings settings = cloudWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);

        cloudWebView.setWebViewClient(new WebViewClient());
        cloudWebView.loadUrl("https://puffin.com");
    }

    @Override
    public void onBackPressed() {
        if (cloudWebView != null && cloudWebView.canGoBack()) {
            cloudWebView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
