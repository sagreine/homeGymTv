/**
 * CustomMediaPlayerService.java
 *
 * Copyright (c) 2015 Amazon Technologies, Inc. All rights reserved.
 *
 * PROPRIETARY/CONFIDENTIAL
 *
 * Use is subject to license terms.
 */

package com.amazon.whisperplay.fling.media.player;

import android.content.Intent;
import android.os.IBinder;
import android.view.SurfaceHolder;

import com.amazon.whisperplay.fling.media.player.activity.IMediaViewControl;
import com.amazon.whisperplay.fling.media.service.MediaPlayerHostService;
import com.amazon.whisperplay.fling.media.service.CustomMediaPlayer;
import com.amazon.whisperplay.fling.media.service.CustomMediaPlayer.StatusListener;
import com.amazon.whisperplay.fling.media.service.MediaPlayerInfo;
import com.amazon.whisperplay.fling.media.service.MediaPlayerStatus;

import java.io.IOException;

/**
 * Custom Player Service.
 */
public class CustomMediaPlayerService extends MediaPlayerHostService {

    /**
     * Custom Player Sample Service ID
     *
     * Custom Player should define its unique service id(SID).
     * And the SID should not be same as FireTV built-in service id, which is "amzn.thin.fling"
     *
     */
    private static final String CUSTOM_PLAYER_SAMPLE_SERVICE_ID = "sagre.HomeGymTV.player";
    private Binder mBinder;
    private CustomMediaPlayerImplementation mImpl;
    private SurfaceHolder mSurfaceHolder;
    private StatusListener mStatusListener;

    /**
     * {@inheritDoc}
     */
    @Override
    public final CustomMediaPlayer createServiceImplementation() {
        mImpl = new CustomMediaPlayerImplementation(this);

        mImpl.startUp();

        if (mSurfaceHolder != null) {
            mImpl.setSurfaceHolder(mSurfaceHolder);
        }
        if (mStatusListener != null) {
            mImpl.addStatusListener(mStatusListener);
        }

        return mImpl;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getPlayerId() {
        return CUSTOM_PLAYER_SAMPLE_SERVICE_ID;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreate() {
        mBinder = new Binder();

        super.onCreate();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onDestroy() {
        mImpl.tearDown();
        mBinder = null;
        mImpl = null;

        super.onDestroy();
    }

    @Override
    public final IBinder onBind(Intent arg0) {
        return mBinder;
    }

    /**
     * Binder object. Returned by Default player service when bound by the MediaViewer,
     * and used to communicate between the Viewer and the service.
     */
    protected class Binder extends android.os.Binder implements IMediaViewControl {
        @Override
        public synchronized void setSurfaceHolder(SurfaceHolder sh) {
            mSurfaceHolder = sh;
            if (mImpl != null) {
                mImpl.setSurfaceHolder(mSurfaceHolder);
            }
        }

        @Override
        public void setImageComplete(boolean result) {
            if (mImpl != null) {
                mImpl.setImageComplete(result);
            }
        }

        @Override
        public void setBinderStatus(boolean status) {
            if (mImpl != null) {
                mImpl.setBinderStatus(status);
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public MediaPlayerStatus getStatus() {
            if (mImpl != null) {
                return mImpl.getStatus();
            }
            return null;
        }

        @Override
        public void setState(MediaPlayerStatus.MediaState media, boolean SendEvent, boolean forceSend) {
            if (mImpl != null) {
                 mImpl.setState(media, SendEvent, forceSend);
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getTitle() {
            if (mImpl != null) {
                return mImpl.getTitle();
            }
            return null;
        }

        @Override
        public String getDescription() {
            if (mImpl != null) {
                return mImpl.getDescription();
            }
            return null;
        }

        @Override
        public int getRestInterval() {
            if (mImpl != null) {
                return mImpl.getRestInterval();
            }
            return 0;
        }

        @Override
        public int getReps() {
            if (mImpl != null) {
                return mImpl.getReps();
            }
            return 0;
        }
        @Override
        public int getWeight() {
            if (mImpl != null) {
                return mImpl.getWeight();
            }
            return 0;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public double getVolume() throws IOException {
            if (mImpl != null) {
                return mImpl.getVolume();
            }
            return -1L;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void setVolume(double volume) throws IOException {
            if (mImpl != null) {
                mImpl.setVolume(volume);
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isMute() throws IOException {
            if (mImpl != null) {
                return mImpl.isMute();
            }
            return false;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void setMute(boolean mute) throws IOException {
            if (mImpl != null) {
                mImpl.setMute(mute);
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public long getPosition() throws IOException {
            if (mImpl != null) {
                mImpl.getPosition();
            }
            return -1L;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public long getDuration() throws IOException {
            if (mImpl != null) {
                return mImpl.getDuration();
            }
            return -1L;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isMimeTypeSupported(String mimeType) throws IOException {
            if (mImpl != null) {
                return mImpl.isMimeTypeSupported(mimeType);
            }
            return false;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void pause() throws IOException {
            if (mImpl != null) {
                mImpl.pause();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void play() throws IOException {
            if (mImpl != null) {
                mImpl.play();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void stop() throws IOException {
            if (mImpl != null) {
                mImpl.stop();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void seek(PlayerSeekMode mode, long positionMilliseconds) throws IOException {
            if (mImpl != null) {
                mImpl.seek(mode, positionMilliseconds);
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void setMediaSource(String mediaLoc, String mediaTitle, boolean autoPlay,
                                   boolean playInBg) throws IOException {
            if (mImpl != null) {
                mImpl.setMediaSource(mediaLoc, mediaTitle, autoPlay, playInBg);
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void setPlayerStyle(String styleJson) {
            if (mImpl != null) {
                mImpl.setPlayerStyle(styleJson);
            }
        }

        @Override
        public void addStatusListener(StatusListener l) {
            if( mStatusListener == null ) {
                mStatusListener = l;
            }
            if (mImpl != null) {
                mImpl.addStatusListener(l);
            }
        }

        @Override
        public void removeStatusListener(StatusListener l) {
            if( mStatusListener == l ) {
                mStatusListener = null;
            }

            if (mImpl != null) {
                mImpl.removeStatusListener(l);
            }
        }

        @Override
        public void setPositionUpdateInterval(long freqMs) throws IOException {
            if (mImpl != null) {
                mImpl.setPositionUpdateInterval(freqMs);
            }
        }

        @Override
        public void sendCommand(String command) throws IOException {
            if (mImpl != null) {
                mImpl.sendCommand(command);
            }
        }

        @Override
        public MediaPlayerInfo getMediaInfo() throws IOException {
            if (mImpl != null) {
                return mImpl.getMediaInfo();
            }
            return null;
        }
    }
}
