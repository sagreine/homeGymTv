package com.amazon.whisperplay.fling.media.player;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.Chronometer;
import android.widget.TextView;

import com.amazon.whisperplay.fling.media.service.MediaPlayerStatus;

public class LauncherSplashActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launcher_splash);
        final TextView mCountUpTimer = (TextView) findViewById(R.id.count_up_timer);

        if (getActionBar() != null) {
            getActionBar().hide();
        }
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_FULLSCREEN);
        Chronometer simpleChronometer = (Chronometer) findViewById(R.id.count_up_timer); // initiate a chronometer
        simpleChronometer.start();

    }


    @Override
    protected void onPause() {
        super.onPause();
        finish();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        // Do nothing when screen rotates.
        super.onConfigurationChanged(newConfig);
    }
}
