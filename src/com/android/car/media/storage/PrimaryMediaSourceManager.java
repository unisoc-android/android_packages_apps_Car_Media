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
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.car.media.common.MediaConstants;
import com.android.car.media.common.source.MediaSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Manages the primary media source for Media Center. The primary source can be obtained
 * using {@link MediaSourceContentProvider}.
 * The primary source can be changed by the app picker in Media Center, or by playing another media
 * app via some other means (e.g. Assistant). This class listens for playback changes on all active
 * MediaControllers, and updates the primary source if a new source begins playback.
 */
public class PrimaryMediaSourceManager {
    private static final String TAG = "PrimaryMSM";

    private Context mContext;
    private MediaSourceStorage mMediaSourceStorage;
    private MediaSessionManager mMediaSessionManager;
    private MediaSessionUpdater mMediaSessionUpdater;
    private MediaSource mPrimaryMediaSource;
    // MediaController for the primary media source. Can be null if the primary media source has not
    // played any media yet.
    private MediaController mPrimaryMediaController;

    private static PrimaryMediaSourceManager sInstance;

    private MediaSessionManager.OnActiveSessionsChangedListener mSessionChangeListener =
            controllers -> {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    String packages = "[";
                    for (MediaController controller : controllers) {
                        packages += controller.getPackageName() + ",";
                    }
                    packages += "]";
                    Log.d(TAG, "OnActiveSessionsChangedListener: " + packages);
                }
                mMediaSessionUpdater.registerCallbacks(controllers);
            };


    private class MediaControllerCallback extends MediaController.Callback {

        private final MediaController mMediaController;

        private MediaControllerCallback(MediaController mediaController) {
            mMediaController = mediaController;
        }

        private void register() {
            mMediaController.registerCallback(this);
        }

        private void unregister() {
            mMediaController.unregisterCallback(this);
        }

        @Override
        public void onPlaybackStateChanged(@Nullable PlaybackState state) {
            if (state.getState() == PlaybackState.STATE_PLAYING) {
                updatePrimaryMediaSourceWithCurrentlyPlaying(
                        Collections.singletonList(mMediaController));
            }
        }
    }


    private class MediaSessionUpdater {
        private Map<MediaSession.Token, MediaControllerCallback> mCallbacks = new HashMap<>();

        /**
         * Register a {@link MediaControllerCallback} for each given controller. Note that if a
         * controller was already watched, we don't register a callback again. This prevents an
         * undesired revert of the primary media source. Callbacks for previously watched
         * controllers that are not present in the given list are unregistered.
         */
        private void registerCallbacks(List<MediaController> newControllers) {

            List<MediaController> additions = new ArrayList<>(newControllers.size());
            Map<MediaSession.Token, MediaControllerCallback> updatedCallbacks =
                    new HashMap<>(newControllers.size());

            for (MediaController controller : newControllers) {
                MediaSession.Token token = controller.getSessionToken();
                MediaControllerCallback callback = mCallbacks.get(token);
                if (callback == null) {
                    callback = new MediaControllerCallback(controller);
                    callback.register();
                    additions.add(controller);
                }
                updatedCallbacks.put(token, callback);
            }

            for (MediaSession.Token token : mCallbacks.keySet()) {
                if (!updatedCallbacks.containsKey(token)) {
                    mCallbacks.get(token).unregister();
                }
            }

            mCallbacks = updatedCallbacks;
            updatePrimaryMediaSourceWithCurrentlyPlaying(additions);
        }
    }

    private PrimaryMediaSourceManager(Context context) {
        mContext = context;
        mMediaSessionManager = mContext.getSystemService(MediaSessionManager.class);
        mMediaSessionUpdater = new MediaSessionUpdater();
        mMediaSourceStorage = new MediaSourceStorage(context);
        mPrimaryMediaSource = mMediaSourceStorage.getLastMediaSource();

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Initial source: " + MediaSource.getPackageName(mPrimaryMediaSource));
        }

        // Add callbacks after initializing the object (b/122845938).
        mMediaSessionManager.addOnActiveSessionsChangedListener(mSessionChangeListener, null);
        mMediaSessionUpdater.registerCallbacks(mMediaSessionManager.getActiveSessions(null));
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

        if (mPrimaryMediaController != null) {
            MediaController.TransportControls controls =
                    mPrimaryMediaController.getTransportControls();
            if (controls != null) {
                controls.pause();
            }
        }

        mPrimaryMediaSource = mediaSource;
        mPrimaryMediaController = null;
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
    private void updatePrimaryMediaSourceWithCurrentlyPlaying(List<MediaController> controllers) {
        for(MediaController controller : controllers) {
            if (controller.getPlaybackState() != null
                    && controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING) {
                if (mPrimaryMediaSource == null || !mPrimaryMediaSource.getPackageName().equals(
                        controller.getPackageName())) {
                    setPrimaryMediaSource(new MediaSource(mContext, controller.getPackageName()));
                }
                // The primary MediaSource can be set via the content provider (e.g from app picker)
                // and the MediaController will enter playing state some time after. This avoids
                // re-setting the primary media source every time the MediaController changes state.
                // Also, it's possible that a MediaSource will create a new MediaSession without
                // us ever changing sources, which is we overwrite our previously saved controller.
                if (mPrimaryMediaSource.getPackageName().equals(controller.getPackageName())) {
                    mPrimaryMediaController = controller;
                }
                return;
            }
        }
    }
}
