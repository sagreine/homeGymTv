/**
 * CustomMediaPlayerImplementation.java
 *
 * Copyright (c) 2015 Amazon Technologies, Inc. All rights reserved.
 *
 * PROPRIETARY/CONFIDENTIAL
 *
 * Use is subject to license terms.
 */

package com.amazon.whisperplay.fling.media.player;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaPlayer;
import android.os.Handler;
import android.util.Log;
import android.view.SurfaceHolder;

import com.amazon.whisperplay.fling.media.player.activity.MediaViewer;
import com.amazon.whisperplay.fling.media.service.MediaPlayerInfo;
import com.amazon.whisperplay.fling.media.service.MediaPlayerStatus;
import com.amazon.whisperplay.fling.media.service.CustomMediaPlayer;
import com.amazon.whisperplay.fling.media.service.MediaPlayerStatus.MediaCondition;
import com.amazon.whisperplay.fling.media.service.MediaPlayerStatus.MediaState;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Simple Android 'MediaPlayer' implementation of a the CustomMediaPlayer
 */
public class CustomMediaPlayerImplementation implements CustomMediaPlayer {
    private static final String TAG = "CustomMediaPlayerImpl";

    private static final long DEFAULT_UPDATE_INTERVAL = 3000L;
    private static final int ERROR_PLAYER_RESET = -38;

    private MediaPlayer mPlayer;
    private AudioManager mAudio;
    private CommandQueue mQueue;
    private volatile MediaState mState = MediaState.NoSource;
    private volatile MediaCondition mError = MediaCondition.Good;
    private SurfaceHolder mSurfaceHolder;
    private double mMaxVolume = 0.0;
    private boolean mMute;
    private List<String> mMimeTypes;
    private String mCurrentTitle;
    private String mCurrentDescription;
    private int mRestInterval;
    private String mMediaType;
    private MediaPlayerInfo mPendingMediaInfo = null;
    private MediaPlayerInfo mCurrentMediaInfo = null;

    private Context mContext;
    private List<StatusListener> mListeners = new ArrayList<StatusListener>();
    private long mUpdateInterval = DEFAULT_UPDATE_INTERVAL;
    private Handler mHandler;

    private boolean mImageMarker = false;
    private boolean mServiceBind = false;
    private final Object mBinderLock = new Object();

    private static final int MEDIA_TYPE_VIDEO = 0;
    private static final int MEDIA_TYPE_AUDIO = 1;
    private static final int MEDIA_TYPE_IMAGE = 2;
    private static final int MEDIA_TYPE_UNKNOWN = 3;

    private static final String[] VIDEO_EXTENSIONS = {"mp4", "3gp", "m4v"};
    private static final String[] AUDIO_EXTENSIONS = {"m4a", "mp3", "ogg", "wav", "aac", "wma", "flac"};
    private static final String[] IMAGE_EXTENSIONS = {"jpeg", "jpg", "png", "bmp"};

    /**
     * Constructor.
     *
     * @param ctx
     *            Context
     */
    public CustomMediaPlayerImplementation(Context ctx) {
        mContext = ctx;
        mHandler = new Handler(ctx.getMainLooper());
    }

    /**
     * {@inheritDoc}
     */
    public void startUp() {
        mAudio = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
    }

    /**
     * {@inheritDoc}
     */
    public void tearDown() {
        if (mPlayer != null) {
            if (mQueue != null) {
                mQueue.flush();
                mQueue.destroy();
            }
            mPlayer.stop();
            mPlayer.release();
            mPlayer = null;
        }
    }

    public void setSurfaceHolder(SurfaceHolder shold) {
        mSurfaceHolder = shold;
        if (mSurfaceHolder != null) {
            mSurfaceHolder.addCallback(new SurfaceHolder.Callback() {

                @SuppressLint("NewApi")
                @Override
                public void surfaceDestroyed(SurfaceHolder sh) {
                    if (mPlayer != null) {
                        mPlayer.setSurface(null);
                    }
                }

                @SuppressLint("NewApi")
                @Override
                public void surfaceCreated(SurfaceHolder sh) {
                    if (mPlayer != null) {
                        mPlayer.setSurface(sh.getSurface());
                    }
                }

                @SuppressLint("NewApi")
                @Override
                public void surfaceChanged(SurfaceHolder arg0, int arg1, int arg2, int arg3) {
                }
            });
        }
    }

    public String getTitle() {
        return mCurrentTitle;
    }

    public String getDescription() {
        return mCurrentDescription;
    }

    public int getRestInterval() {
        return mRestInterval;
    }

    public void setBinderStatus(boolean status) {
        synchronized (mBinderLock) {
            mServiceBind = status;
            if (mServiceBind) {
                mBinderLock.notify();
            }
        }
    }

    public void setImageComplete(boolean result) {
        if (result) {
            mImageMarker = true;
            mCurrentMediaInfo = new MediaPlayerInfo(
                    mPendingMediaInfo.getSource(),
                    mPendingMediaInfo.getMetadata(),
                    Long.toString(0));
            setState(MediaState.Playing, false);
            sendStatus();
        } else {
            mImageMarker = false;
            setState(MediaState.Error);
        }
    }

    /*
     * Determine maximum volume for scaling
     */
    private synchronized void initMaxVolume() {
        if (mMaxVolume == 0.0) {
            mMaxVolume = (double) mAudio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            Log.d(TAG, "Max Volume set to:" + mMaxVolume);
        }
    }

    /*
     * Get the volume from Device's System Stream Music.
     *
     * Note: FireTV built-in receiver doesn't support setting and muting volume.
     *       The user custom player still call this method to handle by itself.
     */
    @Override
    public double getVolume() throws IOException {
        initMaxVolume();
        return ((double) mAudio.getStreamVolume(AudioManager.STREAM_MUSIC)) / mMaxVolume;
    }

    /*
     * Set the volume of Device's System Stream Music.
     *
     * Note: FireTV built-in receiver doesn't support setting and muting volume.
     *       The user custom player still call this method to handle by itself.
     */
    @Override
    public void setVolume(double volume) throws IOException {
        if (volume < 0.0 || volume > 1.0) {
            throw new IllegalArgumentException("Bad value for Volume");
        }

        initMaxVolume();
        mAudio.setStreamVolume(AudioManager.STREAM_MUSIC, (int) (volume * mMaxVolume),
                AudioManager.FLAG_SHOW_UI);
        sendStatus();
        Log.d(TAG, "Set Volume to:" + (int) (volume * mMaxVolume));
    }

    /*
     * Enable or disable mute of Device's System Stream Music.
     *
     * Note: FireTV built-in receiver doesn't support setting and muting volume.
     *       The user custom player still call this method to handle by itself.
     */
    @Override
    public synchronized void setMute(boolean mute) throws IOException {
        mAudio.setStreamMute(AudioManager.STREAM_MUSIC, mute);
        mMute = mute;
        sendStatus();
    }

    @Override
    public synchronized boolean isMute() throws IOException {
        return mMute;
    }

    @Override
    public synchronized long getPosition() throws IOException {
        if (mPlayer == null || mQueue == null || mState == MediaState.Error) {
            throw new IllegalStateException("No Media Stream Set");
        }
        return !mImageMarker? mPlayer.getCurrentPosition() : 0 ;
    }

    @Override
    public synchronized long getDuration() throws IOException {
        if (mPlayer == null || mQueue == null || mState == MediaState.Error
                || mState == MediaState.PreparingMedia) {
            Log.e(TAG, "No Media Stream Set");
            return 0; // return initial value instead.
        }
        return !mImageMarker? mPlayer.getDuration() : 0 ;
    }

    @Override
    public synchronized MediaPlayerStatus getStatus() {
        MediaPlayerStatus mediaPlayerStatus =  new MediaPlayerStatus(mState, mError);
        mediaPlayerStatus.setMute(mMute);
        try {
            mediaPlayerStatus.setVolume(getVolume());
        } catch (IOException e) {
            Log.e(TAG, "Cannot get volume: ", e);
        }
        return mediaPlayerStatus;
    }

    @Override
    public MediaPlayerInfo getMediaInfo() throws IOException {
        Log.i(TAG, "getMediaInfo called. mCurrentMediaInfo = " + mCurrentMediaInfo);
        return mCurrentMediaInfo != null ? mCurrentMediaInfo : new MediaPlayerInfo("", "", "");
    }

    @SuppressLint("NewApi")
    private synchronized void initMimeTypes() {
        if (mMimeTypes == null) {
            mMimeTypes = new LinkedList<String>();

            int numCodecs = MediaCodecList.getCodecCount();

            Log.d(TAG, "Generating mime-type list");
            for (int i = 0; i < numCodecs; i++) {
                MediaCodecInfo codec = MediaCodecList.getCodecInfoAt(i);
                Log.d(TAG, "  Codec:" + codec.getName());

                String[] types = codec.getSupportedTypes();
                for (String type : types) {
                    Log.d(TAG, "  Mime:" + type);
                    mMimeTypes.add(type);
                }
            }
        }
    }

    @Override
    public boolean isMimeTypeSupported(String mimeType) throws IOException {
        initMimeTypes();
        return mMimeTypes.contains(mimeType);
    }

    @Override
    public synchronized void pause() throws IOException {
        Log.d(TAG, "Pause Called");
        if (mPlayer == null || mQueue == null || mState != MediaState.Playing) {
            if (mState == MediaState.Seeking || mState == MediaState.Finished) {
                throw new IllegalStateException("Stream Cannot be Paused");
            }
            throw new IllegalStateException("No Media Stream Set");
        }
        if (!mImageMarker) {
            mQueue.pause();
        }
    }

    @Override
    public synchronized void play() throws IOException {
        Log.d(TAG, "Play Called");
        if (!isPlayerActive()) {
            throw new IllegalStateException("No Media Stream Set");
        }
        mQueue.play();
    }

    @Override
    public synchronized void stop() {
        Log.d(TAG, "Stop Called");
        if (!isPlayerActive()) {
            throw new IllegalStateException("No Media Stream Set");
        }
        mQueue.stop();
    }

    @Override
    public synchronized void seek(PlayerSeekMode mode, long positionMilliseconds) throws IOException {
        Log.d(TAG, "Seek Called");
        if (mState == MediaState.Finished) {
            throw new IllegalStateException("Stream cannot be sought");
        }
        if (!isPlayerActive()) {
            throw new IllegalStateException("No Media Stream Set");
        }
        if (!mImageMarker) {
            mQueue.seek(mode, (int) positionMilliseconds);
        }
    }

    @SuppressLint("NewApi")
    @Override
    public synchronized void setMediaSource(String mediaLoc, String metadataJson,
                                            boolean autoPlay, boolean playInBg) throws IOException {
        Log.d(TAG, "setMediaUrl Called. URI=" + mediaLoc);

        if (mediaLoc == null || mediaLoc.length() == 0) {
            throw new IllegalArgumentException("missing location Url");
        }
        // quick validation of URL
        try {
            new URI(mediaLoc);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Cannot parse URL");
        }
        try {
            JSONObject jobj = (JSONObject) new JSONTokener(metadataJson).nextValue();
            mCurrentTitle = jobj.getString("title");
            mMediaType = jobj.optString("type");
            mCurrentDescription = jobj.optString("description");
            mRestInterval = jobj.optInt("restPeriodAfter");
        } catch (JSONException e) {
            Log.e(TAG, "Cannot parse Metadata", e);
            mCurrentTitle = null;
            mMediaType = null;
            mCurrentDescription = null;
        }

        mPendingMediaInfo = new MediaPlayerInfo(mediaLoc, metadataJson, null);
        if (mPlayer == null) {
            mPlayer = new MediaPlayer();
            if (mSurfaceHolder != null) {
                if (mSurfaceHolder.getSurface().isValid()) {
                    mPlayer.setSurface(mSurfaceHolder.getSurface());
                }
            }
            MediaPlayerListener l = new MediaPlayerListener();
            mQueue = new CommandQueue(CustomMediaPlayerImplementation.this);
            mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mPlayer.setOnPreparedListener(l);
            mPlayer.setOnErrorListener(l);
            mPlayer.setOnInfoListener(l);
            mPlayer.setOnCompletionListener(l);
            mPlayer.setOnSeekCompleteListener(l);
        }

        int definedType = checkMediaType(mMediaType, mediaLoc);
        switch (definedType) {
            case MEDIA_TYPE_VIDEO:
            case MEDIA_TYPE_AUDIO:
                mImageMarker = false;
                if (mPlayer != null && mQueue != null) {
                    mQueue.setUrl(mediaLoc, playInBg);
                    if (autoPlay) {
                        mQueue.play();
                    }
                }
                break;

            case MEDIA_TYPE_IMAGE:
                mImageMarker = true;
                if (mQueue == null) {
                    mQueue = new CommandQueue(CustomMediaPlayerImplementation.this);
                }
                mQueue.setImage(mediaLoc, playInBg);
                if (autoPlay) {
                    mQueue.playImage(mediaLoc, playInBg);
                }
                break;

            case MEDIA_TYPE_UNKNOWN:
            default:
                throw new IllegalArgumentException("Wrong media type. received"
                        +" expected=video/,audio/,image/");
        }
    }

    private int checkMediaType(String mediaType, String url) {
        if (mediaType.contains("/")) {
            if (mediaType.indexOf("video/") == 0) {
                return MEDIA_TYPE_VIDEO;
            } else if (mediaType.indexOf("audio/") == 0) {
                return MEDIA_TYPE_AUDIO;
            } else if (mediaType.indexOf("image/") == 0) {
                return MEDIA_TYPE_IMAGE;
            }
        } else if (url.contains(".")) {
            String fileExtension = url.substring(url.lastIndexOf('.') +1).toLowerCase();
            if (Arrays.asList(VIDEO_EXTENSIONS).contains(fileExtension)) {
                return MEDIA_TYPE_VIDEO;
            } else if (Arrays.asList(AUDIO_EXTENSIONS).contains(fileExtension)) {
                return MEDIA_TYPE_AUDIO;
            } else if ((Arrays.asList(IMAGE_EXTENSIONS).contains(fileExtension))) {
                return MEDIA_TYPE_IMAGE;
            }
        }
        return MEDIA_TYPE_UNKNOWN;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setPlayerStyle(String styleJson) {}

    @Override
    public void addStatusListener(StatusListener l) {
        mListeners.add(l);
    }

    @Override
    public void removeStatusListener(StatusListener l) {
        mListeners.remove(l);
    }

    @Override
    public void setPositionUpdateInterval(long freqMs) throws IOException {
        if (freqMs < 0L) {
            throw new IllegalArgumentException("Negative update interval rate");
        }
        mUpdateInterval = freqMs;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void sendCommand(String command) throws IOException {
        // not implemented here since none of the sample clients
        // call the sendCommand API.  If your client calls the sendCommand
        // API, this is where the service could handle it.  For example
        // you could send a JSON string with any amount of information
        // that you would like to pass from client to service.  This can
        // be used to implement features that the SDK does not, such as
        // playlists.
    }

    /*
     * Returns true if player exists, and is not idle, or in finished or error states
     */
    private synchronized boolean isPlayerActive() {
        return !(mPlayer == null || mQueue == null || mState == MediaState.NoSource
                || mState == MediaState.Error || mState == MediaState.Finished);
    }

    /*****************************************/

    /**
     * Returns the current state.
     *
     * @return current state
     */
    protected synchronized MediaState getState() {
        return mState;
    }

    /**
     * Returns the current condition.
     *
     * @return current condition.
     */
    protected synchronized MediaCondition getError() {
        return mError;
    }

    /**
     * Set the current state, and update listener
     *
     * @param state
     *            new state
     */
    protected synchronized void setState(MediaState state) {
        setState(state, true);
    }

    /**
     * Set the current state, and update listener
     *
     * @param state
     *            new state
     */
    protected synchronized void setState(MediaState state, boolean sendEvent) {
        if (state != mState) {
            if (mState == MediaState.Error) {
                if (mError != MediaCondition.WarningContent
                        && mError != MediaCondition.WarningBandwidth) {
                    mError = MediaCondition.Good;
                }
            }
            mState = state;
            if( sendEvent ) {
                // When we start playing, make sure we start updating position as well.
                if (state == MediaState.Playing) {
                    updateStatus();
                } else {
                    sendStatus();
                }
            }
        }
    }

    /**
     * Set the current State & Condition, based on the condition.
     *
     * @param err
     *            New condition.
     */
    protected synchronized void setState(MediaCondition err) {
        setState(err, true);
    }
    /**
     * Set the current State & Condition, based on the condition.
     *
     * @param err
     *            New condition.
     */
    protected synchronized void setState(MediaCondition err, boolean sendEvent) {
        if (MediaState.Error != mState || err != mError) {
            mError = err;
            if (err != MediaCondition.WarningContent && err != MediaCondition.WarningBandwidth
                    && err != MediaCondition.Good) {
                mState = MediaState.Error;
            }
            if( sendEvent ) {
                sendStatus();
            }
        }
    }

    /**
     * Called when the media finishes playing. Tears down message queue, and does some clean-up.
     */
    protected void onComplete() {
        MediaState curState = getState();

        if (curState == MediaState.Finished) {
            return;
        } else if (curState == MediaState.Error) {
            mPlayer.reset();
            mCurrentTitle = null;
            mMediaType = null;
            mCurrentMediaInfo = null;
            mCurrentDescription = null;
            return;
        }
        mCurrentTitle = null;
        mMediaType = null;
        mCurrentMediaInfo = null;
        mCurrentDescription = null;
        boolean isReseting = (curState == MediaState.NoSource || curState == MediaState.PreparingMedia);
        setState(MediaState.Finished, !isReseting);
        if (mPlayer != null && mQueue != null && !isReseting) {
            mQueue.stop();
        }
    }

    /**
     * Update the status listener to the current status
     */
    protected void sendStatus() {
        Log.d(TAG, "statusChange.  State=" + mState.name() + " Condition=" + mError.name());

        if (!mListeners.isEmpty()) {
            long pos = -1L;
            MediaPlayerStatus status = getStatus();
            try {
                if (status.getState() != MediaState.Error) {
                    pos = getPosition();
                }
            } catch (Exception e) {
                // send -1 as pos.
            }
            for (StatusListener listener : mListeners) {
                try {
                    listener.onStatusChange(status, pos);
                } catch (Exception e) {
                    Log.w(TAG, "Exception in status change event", e);
                }
            }
        }
    }

    private Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            if (getState() == MediaState.Playing) {
                Log.d(TAG, "updateStatus - Sending postDelayed updating status");
                mQueue.update();
            }
        }
    };

    protected void updateStatus() {
        if (getState() == MediaState.Playing) {
            sendStatus();
            mHandler.removeCallbacks(updateRunnable);
            if( mUpdateInterval > 0L ) {
                mHandler.postDelayed(updateRunnable, mUpdateInterval);
            }
        }
    }

    protected void stopStatusUpdating() {
        mHandler.removeCallbacks(updateRunnable);
    }

    /**
     * PC Queue used to serialize all the MediaPlayer accesses
     */
    private static class CommandQueue {

        private LinkedBlockingQueue<Command> mCmdQueue = new LinkedBlockingQueue<Command>();

        private MediaPlayer mPlayer;
        private CustomMediaPlayerImplementation mPlayerService;
        private Thread mExec;
        private Object mSeekLock = new Object();
        private Object mPrepLock = new Object();
        private boolean mPrepped = false;
        private boolean mSeekDone = false;

        private enum PlayerCommand {
            SetUri, Play, Pause, Stop, Seek, Update, SetImage, PlayImage
        };

        private static class Command {

            public PlayerCommand mCmd;
            public int mSeekTimeMs;
            public PlayerSeekMode mSeekMode;
            public String mUri;
            public boolean mInBg;

            public Command(PlayerCommand cmd) {
                mCmd = cmd;
            }

            public Command(PlayerCommand cmd, PlayerSeekMode mode, int seekMs) {
                this(cmd);
                mSeekTimeMs = seekMs;
                mSeekMode = mode;
            }

            public Command(PlayerCommand cmd, String uri, boolean inBg) {
                mCmd = cmd;
                mUri = uri;
                mInBg = inBg;
            }
        }

        /**
         * Constructor. Initializes the Queue, and starts the processing thread
         *
         * @param impl
         */
        public CommandQueue(CustomMediaPlayerImplementation impl) {
            mPlayer = impl.mPlayer;
            mPlayerService = impl;
            mExec = new Thread(new Runnable() {

                public void run() {
                    try {
                        while (mPlayer != null) {
                            Command cmd = mCmdQueue.take();
                            try {
                                exec(cmd);
                            } catch (Exception e) {
                                Log.e(TAG, "Error during command execution:", e);
                            }
                        }
                    } catch (InterruptedException e) {
                        Log.i(TAG, "Shutting down media command queue");
                    }
                }
            });
            mExec.start();
        }

        /**
         * Send position updates
         */
        public void update() {
            try {
                mCmdQueue.put(new Command(PlayerCommand.Update));
            } catch (InterruptedException e) {
                Log.w(TAG, "Put interrupted, command ignored");
            }
        }

        /**
         * Add SetImage command to queue
         *
         * @param uri
         *            Image information to load from
         */
        public void setImage(String uri, boolean playInBg) {
            try {
                mCmdQueue.put(new Command(PlayerCommand.SetImage, uri, playInBg));
            } catch (InterruptedException e) {
                Log.w(TAG, "Put interrupted, command ignored");
            }
        }

        /**
         * Add SetUrl command to queue
         *
         * @param uri
         *            URI of media to load from
         */
        public void setUrl(String uri, boolean playInBg) {
            try {
                mCmdQueue.put(new Command(PlayerCommand.SetUri, uri, playInBg));
            } catch (InterruptedException e) {
                Log.w(TAG, "Put interrupted, command ignored");
            }
        }

        /**
         * Add image play command to queue
         */
        public void playImage(String uri, boolean playInBg) {
            try {
                mCmdQueue.put(new Command(PlayerCommand.PlayImage, uri, playInBg));
            } catch (InterruptedException e) {
                Log.w(TAG, "Put interrupted, command ignored");
            }
        }

        /**
         * Add play command to queue
         */
        public void play() {
            try {
                mCmdQueue.put(new Command(PlayerCommand.Play));
            } catch (InterruptedException e) {
                Log.w(TAG, "Put interrupted, command ignored");
            }
        }

        /**
         * Add pause command to queue
         */
        public void pause() {
            try {
                mCmdQueue.put(new Command(PlayerCommand.Pause));
            } catch (InterruptedException e) {
                Log.w(TAG, "Put interrupted, command ignored");
            }
        }

        /**
         * Add stop command to queue
         */
        public void stop() {
            try {
                mCmdQueue.put(new Command(PlayerCommand.Stop));
            } catch (InterruptedException e) {
                Log.w(TAG, "Put interrupted, command ignored");
            }
        }

        /**
         * Add seek command to queue
         *
         * @param timeMs
         *            Time, reletive to stream start, of location to seek to
         */
        public void seek(PlayerSeekMode mode, int timeMs) {
            try {
                mCmdQueue.put(new Command(PlayerCommand.Seek, mode, timeMs));
            } catch (InterruptedException e) {
                Log.w(TAG, "Put interrupted, command ignored");
            }
        }

        /**
         * Flush out queue
         */
        public void flush() {
            mCmdQueue.clear();
        }

        /**
         * Queue clean-up
         */
        public void destroy() {
            flush();
            mPlayer = null;
            mExec.interrupt();
        }

        /**
         * Called when MediaPlayer sends 'prepared' message.
         * Is used when waiting for player to finish loading.
         */
        public void onPrepped(boolean successful) {
            synchronized (mPrepLock) {
                Log.d(TAG, "MediaPlayer Prepped");
                mPrepped = successful;
                if (successful) {
                    mPlayerService.setState(MediaState.ReadyToPlay);
                    // Media is ready to play, so set its media information as current.
                    Log.d(TAG, "onPrepped. Set mCurrentMediaInfo");
                    mPlayerService.mCurrentMediaInfo = new MediaPlayerInfo(
                            mPlayerService.mPendingMediaInfo.getSource(),
                            mPlayerService.mPendingMediaInfo.getMetadata(),
                            Long.toString(mPlayerService.mPlayer.getDuration()));
                }

                mPrepLock.notify();
            }
        }

        /**
         * Called when the MediaPlayer completes seeking.
         * Is used when waiting for the seek to complete.
         */
        public void onSeekComplete() {
            synchronized (mSeekLock) {
                mSeekDone = true;
                mSeekLock.notify();
            }
        }

        /**
         * Execute the top command in the queue
         *
         * @param cmd
         *            Command to execute
         */
        public void exec(Command cmd) {
            Log.d(TAG, "Executing command " + cmd.toString());
            switch (cmd.mCmd) {
                case PlayImage:
                    synchronized (mPlayerService.mBinderLock) {
                        try {
                            if (!mPlayerService.mServiceBind) {
                                mPlayerService.mBinderLock.wait();
                            }
                        } catch (InterruptedException e) {
                            Log.e(TAG, "InterruptedException", e);
                        }
                    }
                    Intent playImage = new Intent("fling.custom.media.player.loadImage");
                    playImage.putExtra("uri", cmd.mUri);
                    mPlayerService.mContext.sendBroadcast(playImage);
                    break;
                case Play:
                    // Always wait until prepared
                    synchronized (mPrepLock) {
                        try {
                            Log.d(TAG, "Before Play, Waiting for player...");
                            if (!mPrepped) {
                                mPrepLock.wait();
                            }
                        } catch (InterruptedException e) {
                        }
                    }
                    Log.d(TAG, "Before Play, are we prepped? " + (mPrepped ? "Yes" : "No"));
                    if (mPrepped) {
                        Log.d(TAG, "Player Prepped, state = " + mPlayerService.getState());
                        MediaState originalState = mPlayerService.getState();
                        if (originalState == MediaState.Paused || originalState == MediaState.ReadyToPlay) {
                            try {
                                mPlayer.start();
                                mPlayerService.setState(MediaState.Playing);
                                Log.d(TAG, "Player Started...");
                            } catch (Exception e) {
                                Log.e(TAG, "Play Failed:", e);
                            }
                        } else {
                            Log.w(TAG, "Cannot Play, bad state:" + mPlayerService.getState().name());
                        }
                    }
                    break;
                case Pause:
                    // Always wait until prepared
                    synchronized (mPrepLock) {
                        try {
                            Log.d(TAG, "Before Pause, Waiting for player...");
                            if (!mPrepped) {
                                mPrepLock.wait();
                            }
                        } catch (InterruptedException e) {
                        }
                    }
                    if (mPrepped) {
                        if (mPlayerService.getState() == MediaState.Playing
                                || mPlayerService.getState() == MediaState.ReadyToPlay) {
                            try {
                                mPlayer.pause();
                                mPlayerService.setState(MediaState.Paused);
                                Log.d(TAG, "Player Paused...");
                            } catch (Exception e) {
                                Log.e(TAG, "Pause Failed:", e);
                            }
                        } else {
                            Log.w(TAG, "Cannot Pause, bad state:" + mPlayerService.getState().name());
                        }
                    }
                    break;
                case Stop:
                    if (mPlayerService.mImageMarker) {
                        mPlayerService.setState(MediaState.Finished);
                        mPlayerService.mCurrentMediaInfo = null;
                        Log.d(TAG, "Displaying image stopped...");
                        break;
                    }
                    // Always wait until prepared
                    synchronized (mPrepLock) {
                        try {
                            Log.d(TAG, "Before Stop, Waiting for player...");
                            if (!mPrepped) {
                                mPrepLock.wait();
                            }
                        } catch (InterruptedException e) {
                        }
                    }
                    if (mPrepped) {
                        try {
                            mPlayer.stop();
                            mPlayerService.setState(MediaState.Finished);
                            mPlayerService.mCurrentMediaInfo = null;
                            Log.d(TAG, "Player Stopped...");
                        } catch (Exception e) {
                            Log.e(TAG, "Stop Failed:", e);
                        }
                    } else {
                        Log.w(TAG, "Cannot Stop, bad state:" + mPlayerService.getState().name());
                    }
                    break;
                case Seek:
                    // Always wait until prepared
                    synchronized (mPrepLock) {
                        try {
                            Log.d(TAG, "Before Seek, Waiting for player...");
                            if (!mPrepped) {
                                mPrepLock.wait();
                            }
                        } catch (InterruptedException e) {
                        }
                    }
                    if (mPrepped) {
                        MediaState original = mPlayerService.getState();

                        try {
                            mSeekDone = false;
                            int seekTo = cmd.mSeekTimeMs;
                            Log.d(TAG, "Seek TimeMs - " + convertTime(seekTo));
                            if( cmd.mSeekMode == PlayerSeekMode.Relative ) {
                                seekTo += mPlayer.getCurrentPosition();
                            }
                            Log.d(TAG, "Seek to - " + convertTime(seekTo));
                            mPlayer.seekTo(seekTo);
                            mPlayerService.setState(MediaState.Seeking);
                            Log.d(TAG, "Player Seeking...");
                        } catch (Exception e) {
                            Log.e(TAG, "Seek Failed:", e);
                            break;
                        }

                        // Wait for seek to complete, before going to next command.
                        synchronized (mSeekLock) {
                            try {
                                while (!mSeekDone) {
                                    mSeekLock.wait();
                                }
                            } catch (InterruptedException e) {
                            }

                            if (mSeekDone) {
                                Log.d(TAG, "Player Seek Complete...");
                                mPlayerService.setState(original);
                            }
                        }
                    } else {
                        Log.w(TAG, "Cannot Seek, not prepped");
                    }
                    break;
                case Update:
                    mPlayerService.updateStatus();
                    break;
                case SetImage:
                    mPlayerService.setState(MediaState.NoSource, false);
                    mPlayerService.setState(MediaCondition.Good, false);
                    mPlayer.reset();
                    // Launch the viewer for media surface as well.
                    if (!cmd.mInBg && mPlayerService.mSurfaceHolder == null) {
                        Intent it = new Intent(mPlayerService.mContext, MediaViewer.class);
                        it.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        it.putExtra("actualServiceClassname", mPlayerService.mContext.getClass().getName());
                        mPlayerService.mContext.startActivity(it);
                    }
                    mPlayerService.stopStatusUpdating();
                    mPlayerService.setState(MediaState.PreparingMedia);
                    URLConnection connection = null;
                    boolean isImage = false;
                    // Image url validation
                    try {
                        connection = new URL(cmd.mUri).openConnection();
                        String contentType = connection.getHeaderField("Content-Type");
                        isImage = contentType.startsWith("image/");
                    } catch (IOException e) {
                        Log.e(TAG, "Error validating image url", e);
                        mPlayerService.setState(MediaCondition.ErrorUnknown);
                        break;
                    }
                    if (isImage) {
                        mPlayerService.setState(MediaState.ReadyToPlay);
                    } else {
                        Log.e(TAG, "Given url is not an image...");
                        mPlayerService.setState(MediaCondition.ErrorUnknown);
                        break;
                    }
                    break;
                case SetUri:
                    Log.d(TAG, "Before Set Data Source, reset player...");

                    mPlayerService.setState(MediaState.NoSource, false);
                    mPlayerService.setState(MediaCondition.Good, false);
                    mPlayer.reset();
                    // First, launch the viewer for media surface
                    try {
                        if (!cmd.mInBg && mPlayerService.mSurfaceHolder == null) {
                            Intent intent = new Intent(mPlayerService.mContext, MediaViewer.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            intent.putExtra("actualServiceClassname", mPlayerService.mContext.getClass().getName());
                            mPlayerService.mContext.startActivity(intent);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error launching viewer", e);
                        mPlayerService.setState(MediaCondition.ErrorUnknown);
                        break;
                    }
                    Log.d(TAG, "Setting data source to " + cmd.mUri);
                    try {
                        mPlayer.setDataSource(cmd.mUri);
                        mPlayerService.setState(MediaState.PreparingMedia);
                    } catch (IllegalStateException e) {
                        Log.e(TAG, "Error setting data source", e);
                        mPlayerService.setState(MediaCondition.ErrorUnknown);
                        break;
                    } catch (IllegalArgumentException e) {
                        Log.e(TAG, "Error setting data source", e);
                        mPlayerService.setState(MediaCondition.ErrorContent);
                        break;
                    } catch (IOException e) {
                        Log.e(TAG, "Error setting data source", e);
                        mPlayerService.setState(MediaCondition.ErrorChannel);
                        break;
                    } catch (Exception e) {
                        Log.e(TAG, "Error setting data source", e);
                        mPlayerService.setState(MediaCondition.ErrorUnknown);
                        break;
                    }
                    synchronized (mPrepLock) {
                        mPrepped = false;
                        mPlayer.prepareAsync();
                    }
                    break;
            }
        }
    }

    /*
     * Listen to the MediaPlayers various state changes, and complaints.
     */
    private class MediaPlayerListener implements MediaPlayer.OnCompletionListener,
            MediaPlayer.OnErrorListener, MediaPlayer.OnInfoListener, MediaPlayer.OnPreparedListener,
            MediaPlayer.OnSeekCompleteListener {

        @Override
        public void onSeekComplete(MediaPlayer player) {
            if (mQueue != null) {
                mQueue.onSeekComplete();
            }
        }

        @Override
        public void onPrepared(MediaPlayer player) {
            if (mQueue != null) {
                mQueue.onPrepped(true);
            }
        }

        @Override
        public boolean onError(MediaPlayer player, int what, int extra) {
            Log.e(TAG, "MediaPlayer error:" + what + " extra:" + extra);

            switch (what) {
                case MediaPlayer.MEDIA_ERROR_IO:
                case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
                case MediaPlayer.MEDIA_ERROR_TIMED_OUT:
                    setState(MediaCondition.ErrorChannel);
                    break;
                case MediaPlayer.MEDIA_ERROR_MALFORMED:
                case MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK:
                    setState(MediaCondition.ErrorContent);
                    break;
                case ERROR_PLAYER_RESET:
                    // Ignore
                    break;
                case MediaPlayer.MEDIA_ERROR_UNSUPPORTED:
                case MediaPlayer.MEDIA_ERROR_UNKNOWN:
                default:
                    // If the media preparation has started, chances are the queue is waiting
                    // on the mPrepLock. If an error occurs in this state, the queue needs to
                    // stop waiting.
                    if ((mState == MediaState.PreparingMedia) && (mQueue != null)) {
                        mQueue.onPrepped(false);
                    }
                    setState(MediaCondition.ErrorUnknown);
                    break;
            }
            return false;
        }

        @Override
        public boolean onInfo(MediaPlayer player, int what, int extra) {
            Log.e(TAG, "MediaPlayer InfoErr:" + what + " extra:" + extra);
            switch (what) {
                case MediaPlayer.MEDIA_INFO_BUFFERING_START:
                    setState(MediaCondition.WarningBandwidth);
                    break;
                case MediaPlayer.MEDIA_INFO_BAD_INTERLEAVING:
                    setState(MediaCondition.WarningContent);
                    break;
                case MediaPlayer.MEDIA_INFO_VIDEO_TRACK_LAGGING:
                    setState(MediaCondition.WarningBandwidth);
                    break;

                case MediaPlayer.MEDIA_INFO_BUFFERING_END:
                case MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START:
                    if (getError() == MediaCondition.WarningBandwidth) {
                        setState(MediaCondition.Good);
                    }
                    break;

                case MediaPlayer.MEDIA_INFO_NOT_SEEKABLE:
                case MediaPlayer.MEDIA_INFO_UNKNOWN:
                case MediaPlayer.MEDIA_INFO_METADATA_UPDATE:
                default:
                    return true;
            }
            return false;
        }

        @Override
        public void onCompletion(MediaPlayer player) {
            onComplete();
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

}
