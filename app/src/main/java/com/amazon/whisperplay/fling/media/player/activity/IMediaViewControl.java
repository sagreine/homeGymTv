/**
 * IMediaViewControl.java
 *
 * Copyright (c) 2015 Amazon Technologies, Inc. All rights reserved.
 *
 * PROPRIETARY/CONFIDENTIAL
 *
 * Use is subject to license terms.
 */

package com.amazon.whisperplay.fling.media.player.activity;

import android.view.SurfaceHolder;

import com.amazon.whisperplay.fling.media.service.CustomMediaPlayer;
import com.amazon.whisperplay.fling.media.service.MediaPlayerStatus;

/**
 * Interface from view to service
 */
public interface IMediaViewControl extends CustomMediaPlayer {

    /**
     * Returns current Media Title, if any
     *
     * @return title or null
     */
    public String getTitle();

    /**
     * Returns current Media Description, if any
     *
     * @return description or null
     */
    public String getDescription();

    /**
     * Returns current Media rest interval, if any
     *
     * @return description or null
     */
    public int getRestInterval();
    public int getReps();
    public int getWeight();


    /**
     * Set the surface holder to display media on
     *
     * @param holder
     *            Surface to use
     */
    public void setSurfaceHolder(SurfaceHolder holder);

    /**
     * Completion of setting image on view
     *
     * @param result
     *            true if if completed or false
     */
    public void setImageComplete(boolean result);

    public void setState(MediaPlayerStatus.MediaState media, boolean sendEvent, boolean forceSend);

    /**
     * Send status of binder connection
     *
     * @param status
     *            true if if completed or false
     */
    public void setBinderStatus(boolean status);
}
