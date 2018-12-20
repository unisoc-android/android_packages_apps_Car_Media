/*
 * Copyright 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.car.media.storage;

import android.content.Context;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.car.media.common.MediaConstants;
import com.android.car.media.common.source.MediaSource;

import java.util.ArrayList;
import java.util.List;


/**
 * Manages the primary media source for Media Center. The primary source can be obtained
 * using {@link MediaSourceContentProvider}.
 * The primary source can be changed by the app picker in Media Center, or by playing another media
 * app via some other means (e.g. Assistant). This class listens for playback changes on all active
 * MediaControllers, and updates the primary source if a new source begins playback.
 */
public class PrimaryMediaSourceManager {
    private Context mContext;
    private MediaSourceStorage mMediaSourceStorage;
    private MediaSessionManager mMediaSessionManager;
    private MediaSessionUpdater mMediaSessionUpdater;
    private MediaSource mPrimaryMediaSource;

    private static PrimaryMediaSourceManager sInstance;

    private MediaSessionManager.OnActiveSessionsChangedListener mSessionChangeListener =
            controllers -> mMediaSessionUpdater.registerCallbacks(controllers);

    private class MediaSessionUpdater {
        private List<MediaController> mControllers = new ArrayList<>();

        private MediaController.Callback mCallback = new MediaController.Callback() {
            @Override
            public void onPlaybackStateChanged(@Nullable PlaybackState state) {
                if (state.getState() == PlaybackState.STATE_PLAYING) {
                    updatePrimaryMediaSourceWithCurrentlyPlaying();
                }
            }
        };

        private void registerCallbacks(List<MediaController> newControllers) {
            for (MediaController oldController : mControllers) {
                oldController.unregisterCallback(mCallback);
            }
            for (MediaController newController : newControllers) {
                newController.registerCallback(mCallback);
            }
            mControllers.clear();
            mControllers.addAll(newControllers);
        }
    }

    private PrimaryMediaSourceManager(Context context) {
        mContext = context;
        mMediaSessionManager = mContext.getSystemService(MediaSessionManager.class);
        mMediaSessionManager.addOnActiveSessionsChangedListener(mSessionChangeListener, null);
        mMediaSessionUpdater = new MediaSessionUpdater();
        mMediaSessionUpdater.registerCallbacks(mMediaSessionManager.getActiveSessions(null));
        mMediaSourceStorage = new MediaSourceStorage(context);
        mPrimaryMediaSource = mMediaSourceStorage.getLastMediaSource();
    }

    @NonNull
    static PrimaryMediaSourceManager getInstance(Context context) {
        if (sInstance != null) return sInstance;
        synchronized (PrimaryMediaSourceManager.class) {
            if (sInstance != null) return sInstance;
            sInstance = new PrimaryMediaSourceManager(context.getApplicationContext());
            return sInstance;
        }
    }

    /**
     * Updates the primary media source, then notifies content observers of the change
     */
    void setPrimaryMediaSource(@Nullable MediaSource mediaSource) {
        if (mPrimaryMediaSource != null && mPrimaryMediaSource.equals((mediaSource))) {
            return;
        }
        mPrimaryMediaSource = mediaSource;
        mMediaSourceStorage.setLastMediaSource(mediaSource);
        mContext.getContentResolver().notifyChange(MediaConstants.URI_MEDIA_SOURCE, null);
    }

    /**
     * Gets the primary media source
     */
    @Nullable
    MediaSource getPrimaryMediaSource() {
        return mPrimaryMediaSource;
    }

    /**
     * Finds the currently playing media source, then updates the active source if different
     */
    private void updatePrimaryMediaSourceWithCurrentlyPlaying() {
        List<MediaController> activeSessions =
                mMediaSessionManager.getActiveSessions(null);
        for(MediaController controller : activeSessions) {
            if (controller.getPlaybackState() != null
                    && controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING) {
                if (mPrimaryMediaSource == null || !mPrimaryMediaSource.getPackageName().equals(
                        controller.getPackageName())) {
                    setPrimaryMediaSource(new MediaSource(mContext, controller.getPackageName()));
                }
                return;
            }
        }
    }
}
