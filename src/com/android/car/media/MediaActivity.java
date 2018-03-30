/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.car.media;

import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;

import com.android.car.media.drawer.MediaDrawerController;

import androidx.car.drawer.CarDrawerActivity;
import androidx.car.drawer.CarDrawerAdapter;

/**
 * This activity controls the UI of media. It also updates the connection status for the media app
 * by broadcast. Drawer menu is controlled by {@link MediaDrawerController}.
 */
public class MediaActivity extends CarDrawerActivity {
    private static final String TAG = "MediaActivity";

    private MediaDrawerController mDrawerController;
    private PlaybackFragment mPlaybackFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setToolbarElevation(0f);

        mDrawerController = new MediaDrawerController(this /* context */, getDrawerController());
        getDrawerController().setRootAdapter(getRootAdapter());

        setMainContent(R.layout.media_activity);
        MediaManager.getInstance(this).addListener(mListener);

        mPlaybackFragment = new PlaybackFragment();
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, mPlaybackFragment)
                .commit();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mDrawerController.cleanup();
    }

    @Override
    protected CarDrawerAdapter getRootAdapter() {
        return mDrawerController == null ? null : mDrawerController.getRootAdapter();
    }

    @Override
    public void onResumeFragments() {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onResumeFragments");
        }

        super.onResumeFragments();
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "onNewIntent(); intent: " + (intent == null ? "<< NULL >>" : intent));
        }

        setIntent(intent);
        getDrawerController().closeDrawer();
    }

    @Override
    public void onBackPressed() {
        mPlaybackFragment.closeOverflowMenu();
        super.onBackPressed();
    }

    private void handleIntent(Intent intent) {
        Bundle extras = null;
        if (intent != null) {
            extras = intent.getExtras();
        }

        // If the intent has a media component name set, connect to it directly
        if (extras != null && extras.containsKey(MediaManager.KEY_MEDIA_PACKAGE) &&
                extras.containsKey(MediaManager.KEY_MEDIA_CLASS)) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Media component in intent.");
            }

            ComponentName component = new ComponentName(
                    intent.getStringExtra(MediaManager.KEY_MEDIA_PACKAGE),
                    intent.getStringExtra(MediaManager.KEY_MEDIA_CLASS)
            );
            MediaManager.getInstance(this).setMediaClientComponent(component);
        } else {
            // TODO (b/77334804): Implement the correct initialization logic when no component is
            // given. For example, it should either connect the user to the currently playing
            // session, bring the user to the app selector, or open the last known media source.
        }

        if (isSearchIntent(intent)) {
            MediaManager.getInstance(this).processSearchIntent(intent);
            setIntent(null);
        }
    }

    /**
     * Returns {@code true} if the given intent is one that contains a search query for the
     * attached media application.
     */
    private boolean isSearchIntent(Intent intent) {
        return (intent != null && intent.getAction() != null &&
                intent.getAction().equals(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH));
    }

    private void sendMediaConnectionStatusBroadcast(ComponentName componentName,
            String connectionStatus) {
        // There will be no current component if no media app has been chosen before.
        if (componentName == null) {
            return;
        }

        Intent intent = new Intent(MediaConstants.ACTION_MEDIA_STATUS);
        intent.setPackage(componentName.getPackageName());
        intent.putExtra(MediaConstants.MEDIA_CONNECTION_STATUS, connectionStatus);
        sendBroadcast(intent);
    }

    private final MediaManager.Listener mListener = new MediaManager.Listener() {
        @Override
        public void onMediaAppChanged(ComponentName componentName) {
            sendMediaConnectionStatusBroadcast(componentName, MediaConstants.MEDIA_CONNECTED);
        }

        @Override
        public void onStatusMessageChanged(String msg) {}
    };
}
