package com.game2048.play_2048;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.util.Log;
import android.view.MotionEvent;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebSettings.RenderPriority;
import android.webkit.WebView;
import android.widget.Toast;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.InterstitialAd;
import com.google.android.gms.ads.MobileAds;
import com.onesignal.OneSignal;

import java.util.Locale;

public class MainActivity extends Activity {

    private static final String MAIN_ACTIVITY_TAG = "2048_MainActivity";

    private WebView mWebView;
    private long mLastBackPress;
    private static final long mBackPressThreshold = 3500;
    private static final String IS_FULLSCREEN_PREF = "is_fullscreen_pref";
    private long mLastTouch;
    private static final long mTouchThreshold = 2000;
    private Toast pressBackToast;

    private final String ADMOB_APP_ID = "ca-app-pub-4759255989930149~5456796888";
    private final String ADMOB_BANNER_AD_ID = "ca-app-pub-4759255989930149/5581754115";
    private final String ADMOB_INTERSTITIAL_AD_ID = "ca-app-pub-4759255989930149/9587134651";

    private AdView mAdView;
    private InterstitialAd mInterstitialAd;

    @SuppressLint({"SetJavaScriptEnabled", "NewApi", "ShowToast", "ClickableViewAccessibility"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Don't show an action bar or title
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        // If on android 3.0+ activate hardware acceleration
        if (Build.VERSION.SDK_INT >= 11) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
        }

        // Apply previous setting about showing status bar or not
        applyFullScreen(isFullScreen());

        // Check if screen rotation is locked in settings
        boolean isOrientationEnabled = false;
        try {
            isOrientationEnabled = Settings.System.getInt(getContentResolver(),
                    Settings.System.ACCELEROMETER_ROTATION) == 1;
        } catch (SettingNotFoundException e) {
            Log.d(MAIN_ACTIVITY_TAG, "Settings could not be loaded");
        }

        // If rotation isn't locked and it's a LARGE screen then add orientation changes based on sensor
        int screenLayout = getResources().getConfiguration().screenLayout
                & Configuration.SCREENLAYOUT_SIZE_MASK;
        if (((screenLayout == Configuration.SCREENLAYOUT_SIZE_LARGE)
                || (screenLayout == Configuration.SCREENLAYOUT_SIZE_XLARGE))
                && isOrientationEnabled) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
        }

        setContentView(R.layout.activity_main);

        /*ChangeLog cl = new ChangeLog(this);
        if (cl.isFirstRun()) {
            cl.getLogDialog().show();
        }*/

        // Load webview with game
        mWebView = findViewById(R.id.mainWebView);
        WebSettings settings = mWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setRenderPriority(RenderPriority.HIGH);
        settings.setDatabasePath(getFilesDir().getParentFile().getPath() + "/databases");

        // If there is a previous instance restore it in the webview
        if (savedInstanceState != null) {
            mWebView.restoreState(savedInstanceState);
        } else {
            // Load webview with current Locale language
            mWebView.loadUrl("file:///android_asset/index.html?lang=" + Locale.getDefault().getLanguage());
        }

        //Toast.makeText(getApplication(), R.string.toggle_fullscreen, Toast.LENGTH_SHORT).show();
        // Set fullscreen toggle on webview LongClick
        mWebView.setOnTouchListener((v, event) -> {
            // Implement a long touch action by comparing
            // time between action up and action down
            long currentTime = System.currentTimeMillis();
            if ((event.getAction() == MotionEvent.ACTION_UP)
                    && (Math.abs(currentTime - mLastTouch) > mTouchThreshold)) {
                boolean toggledFullScreen = !isFullScreen();
                saveFullScreen(toggledFullScreen);
                applyFullScreen(toggledFullScreen);
            } else if (event.getAction() == MotionEvent.ACTION_DOWN) {
                mLastTouch = currentTime;
            }
            // return so that the event isn't consumed but used
            // by the webview as well
            return false;
        });

        pressBackToast = Toast.makeText(getApplicationContext(), R.string.press_back_again_to_exit,
                Toast.LENGTH_SHORT);


        // OneSignal Initialization
        OneSignal.startInit(this)
                .inFocusDisplaying(OneSignal.OSInFocusDisplayOption.Notification)
                .unsubscribeWhenNotificationsAreDisabled(true)
                .init();


        MobileAds.initialize(this, ADMOB_APP_ID);
        AdView adView = new AdView(this);
        adView.setAdSize(AdSize.LARGE_BANNER);
        adView.setAdUnitId(ADMOB_BANNER_AD_ID);

        mAdView = findViewById(R.id.adView);
        AdRequest adRequest = new AdRequest.Builder().addTestDevice("25E662D1EDF4290CE580D9AA9FA945B9").build();
        mAdView.loadAd(adRequest);

        mInterstitialAd = new InterstitialAd(this);
        mInterstitialAd.setAdUnitId(ADMOB_INTERSTITIAL_AD_ID);
        mInterstitialAd.loadAd(adRequest);

        mWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                if (consoleMessage.message().equalsIgnoreCase("GAME OVER")) {
                    Toast.makeText(MainActivity.this, "Game Over!!", Toast.LENGTH_LONG).show();
                }

                if (mInterstitialAd.isLoaded()) {
                    mInterstitialAd.show();
                } else {
                    mInterstitialAd.loadAd(adRequest);
                }

                Handler h = new Handler();
                h.postDelayed(() -> {

                }, 1000);
                return true;
            }
        });
    }

    @JavascriptInterface
    public void onData(String value) {
        //.. do something with the data
    }

    @Override
    protected void onResume() {
        super.onResume();
        mWebView.loadUrl("file:///android_asset/index.html?lang=" + Locale.getDefault().getLanguage());
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        mWebView.saveState(outState);
    }

    /**
     * Saves the full screen setting in the SharedPreferences
     *
     * @param isFullScreen boolean value
     */

    private void saveFullScreen(boolean isFullScreen) {
        // save in preferences
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
        editor.putBoolean(IS_FULLSCREEN_PREF, isFullScreen);
        editor.commit();
    }

    private boolean isFullScreen() {
        return PreferenceManager.getDefaultSharedPreferences(this).getBoolean(IS_FULLSCREEN_PREF,
                true);
    }

    /**
     * Toggles the activity's fullscreen mode by setting the corresponding window flag
     *
     * @param isFullScreen boolean value
     */
    private void applyFullScreen(boolean isFullScreen) {
        if (isFullScreen) {
            getWindow().clearFlags(LayoutParams.FLAG_FULLSCREEN);
        } else {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
    }

    /**
     * Prevents app from closing on pressing back button accidentally.
     * mBackPressThreshold specifies the maximum delay (ms) between two consecutive backpress to
     * quit the app.
     */

    @Override
    public void onBackPressed() {
        long currentTime = System.currentTimeMillis();
        if (Math.abs(currentTime - mLastBackPress) > mBackPressThreshold) {
            pressBackToast.show();
            mLastBackPress = currentTime;
        } else {
            pressBackToast.cancel();
            super.onBackPressed();
            if (mInterstitialAd.isLoaded()) {
                mInterstitialAd.show();
            } else {
                Log.d("TAG", "The interstitial wasn't loaded yet.");
            }
        }
    }
}
