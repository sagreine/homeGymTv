/**
 * MediaViewer.java
 *
 * Copyright (c) 2015 Amazon Technologies, Inc. All rights reserved.
 *
 * PROPRIETARY/CONFIDENTIAL
 *
 * Use is subject to license terms.
 */

package com.sagre1.whisperplay.fling.media.player.activity.activity;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.KeyEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Chronometer;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.sagre1.whisperplay.fling.media.player.R;
import com.amazon.whisperplay.fling.media.service.MediaPlayerInfo;
import com.amazon.whisperplay.fling.media.service.MediaPlayerStatus;
import com.amazon.whisperplay.fling.media.service.MediaPlayerStatus.MediaState;
import com.amazon.whisperplay.fling.media.service.CustomMediaPlayer.PlayerSeekMode;
import com.amazon.whisperplay.fling.media.service.CustomMediaPlayer.StatusListener;
import com.androidquery.AQuery;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.IOException;
import java.lang.reflect.Method;

public class MediaViewer extends Activity {
    private static final String TAG = "MediaViewer";
    // Binder for Player
    private IMediaViewControl mViewControl;
    private boolean mActive;
    private Handler mHandler = new Handler();
    // SurfaceView for MediaPlayer
    private View mPlayerSurfaceView;
    private View mFakeBackground;

    // Media progress bar during preparation
    private ProgressBar mProgressBar;

    // Media information layout with alpha during playing
    private RelativeLayout mMediaInfoLayout;
    private RelativeLayout mMediaInfoPersistentLayout;
    private SeekBar mSeekBar;
    private TextView mMediaTitle;
    private TextView mMediaDescription;
    private TextView mRestInterval;
    private TextView mRepsXWeight;
    private TextView mNLMediaTitle;
    private TextView mNLMediaDescription;
    private TextView mNLRepsXWeight;
    private TextView mAssistancePull;
    private TextView mAssistancePush;
    private TextView mAssistanceCore;
    private TextView mTotalDuration;
    private TextView mCurrentPosition;
    private Chronometer simpleChronometer;
    private Boolean firstExercise;



    // Paused identification letter
    private TextView mPausedLetter;

    // Marker for various states
    private boolean markPaused = false;
    private boolean markFreshInfo = false;
    private boolean markAudio = false;
    private boolean markPicture = false;
    private boolean isCountdownOver = false;
    CountDownTimer timer;

    private AQuery mAQuery;

    private BroadcastReceiver mImageReceiver;

    private StatusListener mStatusListener = new StatusListener() {

        @Override
        public void onStatusChange(MediaPlayerStatus status, long position) {
            mHandler.removeCallbacksAndMessages(null);
            MediaState state = status.getState();
            if (state == MediaState.Error || state == MediaState.Finished) {
                    mHandler.postDelayed(new FinishTask(), 1000L);
            } else {
                setViewForState(state, position);
            }
        }

    };
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder bindTo) {
            Log.d(TAG, "Connected to MediaPlayerService : " + name.flattenToString());
            mViewControl = (IMediaViewControl) bindTo;
            if (mActive) {
                SurfaceView v = (SurfaceView) findViewById(R.id.surfaceViewPlayer);
                mViewControl.setSurfaceHolder(v.getHolder());
                mViewControl.setBinderStatus(true);
            }
            mViewControl.addStatusListener(mStatusListener);
            try {
                MediaPlayerStatus status = mViewControl.getStatus();

                if( status != null ) {
                    setViewForState(status.getState(), 0);
                }
            } catch (IOException e) {
                Log.e(TAG, "Exception controlling media player:", e);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mViewControl = null;
        }

    };

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if( mViewControl == null ) {
            return false;
        }

        try {
            // Handle remote and local keyboard media keys
            switch (keyCode) {
                case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD: {
                    mViewControl.seek(PlayerSeekMode.Relative, 10000);
                }
                    break;
                case KeyEvent.KEYCODE_MEDIA_REWIND: {
                    mViewControl.seek(PlayerSeekMode.Relative, -10000);
                }
                    break;
                case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE: {
                    MediaPlayerStatus status = mViewControl.getStatus();
                    if (status.getState() == MediaState.Playing) {
                        mViewControl.pause();
                    } else if (status.getState() == MediaState.Paused) {
                        mViewControl.play();
                    }
                }
                    break;
                default:
                    return super.onKeyUp(keyCode, event);
            }
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Exception controlling media player:", e);
        }
        return false;
    }

    //// UNTESTED UNTESTED UNTESTED
    @Override
    public void onBackPressed() {
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_media_viewer);
        if (getActionBar() != null) {
            getActionBar().hide();
        }
        mPlayerSurfaceView = findViewById(R.id.surfaceViewPlayer);
        mFakeBackground = findViewById(R.id.fake_background);
        mMediaInfoLayout = (RelativeLayout)findViewById(R.id.media_info_layout);
        //mMediaInfoPersistentLayout = (RelativeLayout)findViewById(R.id.media_info_persistent_layout);
        mSeekBar = (SeekBar)findViewById(R.id.seekBar);
        mMediaTitle = (TextView)findViewById(R.id.media_title);
        mMediaDescription = (TextView)findViewById(R.id.media_description);
        mRestInterval = (TextView)findViewById(R.id.media_rest_interval);
        mRepsXWeight = (TextView)findViewById(R.id.media_weight_x_reps);
        mNLMediaTitle = (TextView)findViewById(R.id.next_lift_title);
        mNLMediaDescription = (TextView)findViewById(R.id.next_lift_description);
        mNLRepsXWeight = (TextView)findViewById(R.id.next_lift_weight_x_reps);
        mAssistancePull = (TextView)findViewById(R.id.assistance_pull);
        mAssistancePush = (TextView)findViewById(R.id.assistance_push);
        mAssistanceCore = (TextView)findViewById(R.id.assistance_core);
        simpleChronometer = (Chronometer) findViewById(R.id.count_up_timer); // initiate a chronometer



        mTotalDuration = (TextView)findViewById(R.id.totalDuration);
        mCurrentPosition = (TextView)findViewById(R.id.currentPosition);
        mPausedLetter = (TextView)findViewById(R.id.paused);
        mProgressBar = (ProgressBar)findViewById(R.id.media_loading_progress);
        mAQuery = new AQuery(this);

        String cname = null;
        try {
            // Because the CustomMediaPlayerService was likely sub-classed, we have to package
            // it class name, and give it to the MediaViewer to bind with.
            Intent intent = getIntent();
            cname = intent.getStringExtra("actualServiceClassname");
            if (cname == null) {
                ActivityInfo actInfo = getPackageManager()
                        .getActivityInfo(getComponentName(), PackageManager.GET_META_DATA);
                Bundle bundle = actInfo.metaData;
                if (bundle != null) {
                    cname = bundle.getString("actualServiceClassname");
                }
            }
            Log.d(TAG, "Binding to :"+cname);
            bindService(new Intent(this, Class.forName(cname)), mConnection, Context.BIND_AUTO_CREATE);
        } catch (ClassNotFoundException e) {
            Log.e(TAG, "Cannot find service class:" + cname);
        } catch (Exception e) {
            Log.e(TAG, "Excepting binding back to service", e);
        }
    }

    /**
     * @{inheritDoc
     */
    @Override
    protected void onDestroy() {
        unbindService(mConnection);
        mViewControl = null;
        super.onDestroy();
    }

    /**
     * @{inheritDoc
     */
    @Override
    protected void onPause() {
        super.onPause();
        mActive = false;
        resetMarkers();
        clearMediaInformationAndHide();
        //pictureViewVisibility(false);
        if (mImageReceiver != null) {
            unregisterReceiver(mImageReceiver);
        }
        if (mViewControl != null) {
            try {
                MediaPlayerStatus status = mViewControl.getStatus();
                if (status.getState() == MediaState.Playing || status.getState() == MediaState.Paused) {

                    mViewControl.stop();
                }
                mViewControl.setSurfaceHolder(null);
                mViewControl.setBinderStatus(false);
            } catch (IOException e) {
                Log.e(TAG, "Exception controlling media player:", e);
            }

        }
        mHandler.postDelayed(new FinishTask(), 1000L);
    }

    /**
     * @{inheritDoc
     */
    @Override
    protected void onResume() {
        super.onResume();

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        mActive = true;
        mHandler.removeCallbacksAndMessages(null);
        clearMediaInformationAndHide();

        if (mViewControl != null) {
            SurfaceView v = (SurfaceView) findViewById(R.id.surfaceViewPlayer);
            mViewControl.setSurfaceHolder(v.getHolder());
        }

        mImageReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                resetMarkers();
                clearMediaInformationAndHide();
                mPlayerSurfaceView.setVisibility(View.GONE);
                markPicture = true;
                //String imageUri = intent.getStringExtra("uri");
                //mAQuery.id(mPictureImageView).image(imageUri, true, true, 0, AQuery.INVISIBLE);
                mViewControl.setImageComplete(true);
            }
        };
        IntentFilter intentFilter = new IntentFilter("fling.custom.media.player.loadImage");
        registerReceiver(mImageReceiver, intentFilter);
    }

    public void setViewForState(final MediaState state, final long position) {
        this.runOnUiThread(new Runnable() {
            @TargetApi(Build.VERSION_CODES.O)
            public void run() {
                if (mViewControl == null) {
                    return;
                }
                switch (state) {
                    case PreparingMedia:
                    case ReadyToPlay:
                        surfaceViewVisibility(false);
                        resetMarkers();
                        clearMediaInformationAndHide();
                        preparationVisibility(true);
                        break;
                    case Seeking:
                        try {
                            long duration = mViewControl.getDuration();
                            mSeekBar.setMax((int) duration);
                            mSeekBar.setProgress((int) position);
                            mTotalDuration.setText(convertTime(duration));
                            mCurrentPosition.setText(convertTime(position));
                        } catch (IOException e) {
                            Log.e(TAG, "IOException", e);
                        }
                        animateMediaInfo(true, markAudio);
                        break;
                    case Paused:
                        mPausedLetter.setVisibility(View.VISIBLE);
                        markPaused = true;
                        animateMediaInfo(false, markAudio);
                        break;
                    case Playing:
                        if (markPicture) {
                            preparationVisibility(false);
                            surfaceViewVisibility(false);
                            break;
                        }
                        if (mPlayerSurfaceView != null && mPlayerSurfaceView instanceof SurfaceView) {
                            preparationVisibility(false);
                            surfaceViewVisibility(true);
                            if (!markFreshInfo) {
                                String media_title = getString(R.string.empty);
                                String description = getString(R.string.empty);
                                String media_type = getString(R.string.empty);
                                int rest_interval = 0;
                                int reps = 0;
                                int weight = 0;
                                String next_lift_title = getString(R.string.empty);
                                String next_lift_description = getString(R.string.empty);
                                int next_lift_reps = 0;
                                int next_lift_weight = 0;

                                String weightShown = "";
                                String next_lift_weightShown = "";

                                int assistance_reps_pull = 0;
                                int assistance_reps_push = 0;
                                int assistance_reps_core = 0;
                                String assistance_lifts_pull = getString(R.string.empty);
                                String assistance_lifts_push = getString(R.string.empty);
                                String assistance_lifts_core = getString(R.string.empty);
                                boolean startTimerCast = false;
                                boolean firstExercise = true;

                                try {
                                    // current lift, with media
                                    MediaPlayerInfo info = mViewControl.getMediaInfo();
                                    JSONTokener js = new JSONTokener(info.getMetadata());
                                    JSONObject jsonObject = (JSONObject) js.nextValue();
                                    media_title = jsonObject.getString("title");
                                    description = jsonObject.optString("description");
                                    rest_interval = Integer.parseInt(jsonObject.optString("restPeriodAfter"));
                                    reps = Integer.parseInt(jsonObject.optString("reps"));
                                    weight = Integer.parseInt(jsonObject.optString("weight"));
                                    media_type = jsonObject.optString("type").split("/")[0];
                                    // next lift, for display
                                    jsonObject = (JSONObject) js.nextValue();
                                    next_lift_title = jsonObject.getString("title");
                                    next_lift_description = jsonObject.optString("description");
                                    next_lift_reps = Integer.parseInt(jsonObject.optString("reps"));
                                    next_lift_weight = Integer.parseInt(jsonObject.optString("weight"));
                                    // we conditionally set a timer to run, so find out if we're setting a timer to run..
                                    //startTimerCast = (boolean) js.nextValue();
                                    startTimerCast = ((JSONObject) js.nextValue()).optBoolean("startTimerCast");
                                    //firstExercise = (boolean) js.nextValue();
                                    firstExercise = ((JSONObject) js.nextValue()).optBoolean("firstExercise");


                                    // assistance lifts...for now

                                    //assistance_reps_pull = Integer.parseInt(jsonObject.optString("assistancePullReps"));
                                    //assistance_reps_push = Integer.parseInt(jsonObject.optString("assistancePushReps"));
                                    //assistance_reps_core = Integer.parseInt(jsonObject.optString("assistanceCoreReps"));
                                    // actually do this correctly later
                                    //assistance_lifts_pull = jsonObject.optString("assistancePull").replaceAll("[^a-zA-Z ,]","");
                                    //assistance_lifts_push = jsonObject.optString("assistancePush").replaceAll("[^a-zA-Z ,]","");
                                    //assistance_lifts_core = jsonObject.optString("assistanceCore").replaceAll("[^a-zA-Z ,]","");
                                    //List<String> list = Arrays.asList(jsonObject.optString("assistancePull"));
                                    //String joined = String.join(", ", list)
                                } catch (IOException e) {
                                    Log.e(TAG, "IOException", e);
                                } catch (JSONException e) {
                                    Log.e(TAG, "JSONException", e);
                                }
                                mMediaTitle.setText(media_title);
                                if(weight == 0)
                                {
                                    weightShown = " reps";
                                }
                                else {
                                    weightShown =  "x" + String.valueOf(weight);
                                }
                                if(next_lift_weight == 0)
                                {
                                    next_lift_weightShown = " reps";
                                }
                                else {
                                    next_lift_weightShown =  "x" + String.valueOf(next_lift_weight);
                                }
                                mRepsXWeight.setText(String.valueOf(reps) + weightShown);
                                mMediaDescription.setText(description);
                                mNLMediaTitle.setText(next_lift_title);
                                mNLRepsXWeight.setText(String.valueOf(next_lift_reps) + next_lift_weightShown);
                                mNLMediaDescription.setText(next_lift_description);
                                //mAssistanceCore.setText(String.valueOf(assistance_reps_core) + " reps of: " + assistance_lifts_core);
                                //mAssistancePull.setText(String.valueOf(assistance_reps_pull) + " reps of: " + assistance_lifts_pull);
                                //mAssistancePush.setText(String.valueOf(assistance_reps_push) + " reps of: " + assistance_lifts_push);
                                 timer = null;

                                mRestInterval.setTextSize(180);
                                if(startTimerCast) {
                                 timer = new CountDownTimer((rest_interval * 1000), 1000) {
                                    public void onTick(long millisUntilFinished) {
                                        mRestInterval.setText(String.valueOf(millisUntilFinished / 1000));
                                        if (millisUntilFinished < 5000) {
                                            // put colors in a resource file instead of doing
                                            mRestInterval.setTextColor(Color.rgb(200, 0, 0));
                                        }
                                    }

                                    public void onFinish() {
                                        //isCountdownOver = true;
                                        mRestInterval.setTextSize(60);
                                        mRestInterval.setTextColor(Color.rgb(255, 255, 255));
                                        mRestInterval.setText("Go lift!");
//need to handle if the timer ends but the media is still playing...
                                        //if (try {mViewControl.getStatus().getState() == MediaState.Finished) {
                                        //}
//}
                                        // this is necessary to have a real app, tells it we're done playing this....
                                        //mViewControl.setState(MediaState.Finished, true, true);
                                        isCountdownOver = true;
                                        //finish();
                                    }
                                }.start();
                                }
                                // we'll have a persistent count up clock at the top after the first exercise.
                                if (firstExercise) {
                                    simpleChronometer.start();
                                }
                                // Assume that metadata:type came into correctly. There is still
                                // possibility that this type can be differ than media type on URL.
                                if (media_type.equals("audio")) {
                                    markAudio = true;
                                    animateMediaInfo(false, markAudio);
                                } else {
                                    animateMediaInfo(true, markAudio);
                                }
                                markFreshInfo = true;
                            }
                            // Playing state received from Paused state
                            if (markPaused) {
                                mPausedLetter.setVisibility(View.GONE);
                                markPaused = false;
                                animateMediaInfo(true, markAudio);
                            }
                            try {
                                long duration = mViewControl.getDuration();
                                mSeekBar.setMax((int) duration);
                                mSeekBar.setProgress((int) position);
                                mTotalDuration.setText(convertTime(duration));
                                mCurrentPosition.setText(convertTime(position));
                            } catch (IOException e) {
                                Log.e(TAG, "IOException", e);
                            }

                            // This will only work / is needed on FireTV and FireTV Stick
                            try {
                                Class<?> navCls = ClassLoader.getSystemClassLoader().loadClass("com.amazon.tv.launcher.Navigator");
                                if (navCls != null) {
                                    android.util.Log.d(TAG, "Found Nav Class");
                                    Method hide = navCls.getMethod("hide", Context.class);
                                    if (hide != null) {
                                        android.util.Log.d(TAG, "Found Hide Method");
                                        hide.invoke(null, this);
                                    } else {
                                        Log.d(TAG, "Hide Method Not Found");
                                    }
                                } else {
                                    Log.d(TAG, "Nav Class Not Found");
                                }
                            } catch (Exception e) {
                                // OK, should fail on non-amazon devices
                            }
                        }
                        break;
                }

            }
        });
    }

    private void resetMarkers() {
        markAudio = false;
        markPaused = false;
        markFreshInfo = false;
        markPicture = false;
    }

    private void surfaceViewVisibility(boolean visible) {
        mPlayerSurfaceView.setVisibility(visible? View.VISIBLE : View.GONE);
    }

    private void preparationVisibility(boolean visible) {
        mFakeBackground.setVisibility(visible? View.VISIBLE : View.GONE);
        mProgressBar.setVisibility(visible? View.VISIBLE : View.GONE);
    }

    private void clearMediaInformationAndHide() {

        mPausedLetter.setVisibility(View.GONE);
        mMediaInfoLayout.animate().cancel();
        mMediaInfoLayout.setVisibility(View.GONE);
        mSeekBar.animate().cancel();
        mSeekBar.setVisibility(View.GONE);
        mCurrentPosition.animate().cancel();
        mCurrentPosition.setVisibility(View.GONE);
        mTotalDuration.animate().cancel();
        mTotalDuration.setVisibility(View.GONE);
        //mMediaTitle.animate().cancel();
        //mMediaTitle.setVisibility(View.GONE);
        //mMediaDescription.animate().cancel();
        //mMediaDescription.setVisibility(View.GONE);
        mSeekBar.setProgress(0);
        mSeekBar.setMax(0);
        mTotalDuration.setText(getString(R.string.init_time));
        mCurrentPosition.setText(getString(R.string.init_time));
        //mMediaTitle.setText(getString(R.string.empty));
        //mMediaDescription.setText(getString(R.string.empty));
    }

    private void animateMediaInfo(boolean fade, boolean isAudio) {
        if (!fade || isAudio) {
            mMediaInfoLayout.animate().cancel();
            mMediaInfoLayout.setAlpha(1f);
            mMediaInfoLayout.setVisibility(View.VISIBLE);
            mSeekBar.animate().cancel();
            mSeekBar.setAlpha(1f);
            mSeekBar.setVisibility(View.VISIBLE);
            mCurrentPosition.animate().cancel();
            mCurrentPosition.setAlpha(1f);
            mCurrentPosition.setVisibility(View.VISIBLE);
            mTotalDuration.animate().cancel();
            mTotalDuration.setAlpha(1f);
            mTotalDuration.setVisibility(View.VISIBLE);
            //mMediaTitle.animate().cancel();
            //mMediaTitle.setAlpha(1f);
            //mMediaTitle.setVisibility(View.VISIBLE);
            //mMediaDescription.animate().cancel();
            //mMediaDescription.setAlpha(1f);
            //mMediaDescription.setVisibility(View.VISIBLE);
        } else {
            mMediaInfoLayout.setAlpha(1f);
            mMediaInfoLayout.setVisibility(View.VISIBLE);
            mSeekBar.setAlpha(1f);
            mSeekBar.setVisibility(View.VISIBLE);
            mCurrentPosition.setAlpha(1f);
            mCurrentPosition.setVisibility(View.VISIBLE);
            mTotalDuration.setAlpha(1f);
            mTotalDuration.setVisibility(View.VISIBLE);
            //mMediaTitle.setAlpha(1f);
            mMediaTitle.setVisibility(View.VISIBLE);
            //mMediaDescription.setAlpha(1f);
            mMediaDescription.setVisibility(View.VISIBLE);
            if (!markPaused) {
                mMediaInfoLayout.animate().alpha(0f).setDuration(5000);
                mSeekBar.animate().alpha(0f).setDuration(5000);
                mCurrentPosition.animate().alpha(0f).setDuration(5000);
                mTotalDuration.animate().alpha(0f).setDuration(5000);
                //mMediaTitle.animate().alpha(0f).setDuration(5000);
                //mMediaDescription.animate().alpha(0f).setDuration(5000);
            }
        }
    }

    private static String convertTime(long time) {
        long totalSecs = time / 1000;
        long hours = totalSecs / 3600;
        long minutes = (totalSecs / 60) % 60;
        long seconds = totalSecs % 60;
        String hourString = (hours == 0) ? "00" : ((hours < 10) ? "0" + hours : "" + hours);
        String minString = (minutes == 0) ? "00" : ((minutes < 10) ? "0" + minutes : "" + minutes);
        String secString = (seconds == 0) ? "00" : ((seconds < 10) ? "0" + seconds : "" + seconds);

        return hourString + ":" + minString + ":" + secString;
    }

    private class FinishTask implements Runnable {
        public void run() {
            if (mViewControl != null) {
                try {
                    MediaState state = mViewControl.getStatus().getState();
                    if(isCountdownOver) {
                        if (!mActive || state == MediaState.Error || state == MediaState.Finished) {

                            Log.e(TAG, "Terminating Player because of:" + (mActive ? "Paused" : state.name()));
//                        finish();
                        }
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Exception controlling media player:", e);
                }
            }
        }
    }
}
