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
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.design.widget.AppBarLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;

import com.android.car.media.common.CrossfadeImageView;
import com.android.car.media.common.MediaItemMetadata;
import com.android.car.media.common.MediaSource;
import com.android.car.media.common.PlaybackControls;
import com.android.car.media.common.PlaybackModel;
import com.android.car.media.drawer.MediaDrawerController;
import com.android.car.media.widgets.AppBarView;
import com.android.car.media.widgets.MetadataView;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import androidx.car.drawer.CarDrawerActivity;
import androidx.car.drawer.CarDrawerAdapter;

/**
 * This activity controls the UI of media. It also updates the connection status for the media app
 * by broadcast. Drawer menu is controlled by {@link MediaDrawerController}.
 */
public class MediaActivity extends CarDrawerActivity implements BrowseFragment.Callbacks {
    private static final String TAG = "MediaActivity";

    /** Intent extra specifying the package with the MediaBrowser **/
    public static final String KEY_MEDIA_PACKAGE = "media_package";

    /** Configuration (controlled from resources) */
    private boolean mContentForwardBrowseEnabled;
    private boolean mForceBrowseTabs;
    private int mMaxBrowserTabs;
    private float mBackgroundBlurRadius;
    private float mBackgroundBlurScale;

    /** Models */
    private MediaDrawerController mDrawerController;
    private MediaSource mMediaSource;
    private PlaybackModel mPlaybackModel;

    /** Layout views */
    private AppBarView mAppBarView;
    private CrossfadeImageView mAlbumBackground;
    private PlaybackFragment mPlaybackFragment;
    private AppBarLayout mDrawerBarLayout;
    private View mBrowseScrim;
    private PlaybackControls mPlaybackControls;
    private MetadataView mMetadataView;
    private ViewGroup mBrowseControlsContainer;

    /** Current state */
    private MediaItemMetadata mCurrentMetadata;
    private Fragment mCurrentFragment;
    private Mode mMode = Mode.BROWSING;

    private MediaSource.Observer mMediaSourceObserver = new MediaSource.Observer() {
        @Override
        protected void onBrowseConnected(boolean success) {
            MediaActivity.this.onBrowseConnected(success);
        }

        @Override
        protected void onBrowseDisconnected() {
            MediaActivity.this.onBrowseConnected(false);
        }
    };
    private MediaSource.ItemsSubscription mRootItemsSubscription =
            (parentId, items) -> updateTabs(items);
    private PlaybackModel.PlaybackObserver mPlaybackObserver =
            new PlaybackModel.PlaybackObserver() {
                @Override
                public void onSourceChanged() {
                    updateMetadata();
                    updateBrowseSource();
                }

                @Override
                public void onMetadataChanged() {
                    updateMetadata();
                }
            };
    private AppBarView.AppBarListener mAppBarListener = new AppBarView.AppBarListener() {
        @Override
        public void onTabSelected(MediaItemMetadata item) {
            mMode = Mode.BROWSING;
            updateBrowseFragment(item);
            updateMetadata();
        }

        @Override
        public void onBack() {
            if (mCurrentFragment != null && mCurrentFragment instanceof BrowseFragment) {
                BrowseFragment fragment = (BrowseFragment) mCurrentFragment;
                fragment.navigateBack();
            }
        }

        @Override
        public void onCollapse() {
            switchToMode(Mode.BROWSING);
        }

        @Override
        public void onAppSelection() {
            // TODO(b/78602199): Implement app selection logic
        }
    };

    private enum Mode {
        BROWSING,
        PLAYBACK
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setMainContent(R.layout.media_activity);

        setToolbarElevation(0f);

        mContentForwardBrowseEnabled = getResources()
                .getBoolean(R.bool.forward_content_browse_enabled);
        mForceBrowseTabs = getResources()
                .getBoolean(R.bool.force_browse_tabs);
        mDrawerController = new MediaDrawerController(this, getDrawerController());
        getDrawerController().setRootAdapter(getRootAdapter());
        mAppBarView = findViewById(R.id.app_bar);
        mAppBarView.setListener(mAppBarListener);
        // TODO(b/78602199): Implement actual app selection logic
        mAppBarView.setAppSelection(true);
        mPlaybackFragment = new PlaybackFragment();
        mPlaybackModel = new PlaybackModel(this);
        mMaxBrowserTabs = getResources().getInteger(R.integer.max_browse_tabs);
        mDrawerBarLayout = findViewById(androidx.car.R.id.appbar);
        mDrawerBarLayout.setVisibility(View.GONE);
        mAlbumBackground = findViewById(R.id.media_background);
        mBrowseScrim = findViewById(R.id.browse_scrim);
        mPlaybackControls = findViewById(R.id.controls);
        mPlaybackControls.setModel(mPlaybackModel);
        mMetadataView = findViewById(R.id.metadata);
        mMetadataView.setModel(mPlaybackModel);
        mBrowseControlsContainer = findViewById(R.id.browse_controls_container);
        mBrowseControlsContainer.setOnClickListener(view -> switchToMode(Mode.PLAYBACK));
        TypedValue outValue = new TypedValue();
        getResources().getValue(R.dimen.playback_background_blur_radius, outValue, true);
        mBackgroundBlurRadius = outValue.getFloat();
        getResources().getValue(R.dimen.playback_background_blur_scale, outValue, true);
        mBackgroundBlurScale = outValue.getFloat();
    }

    @Override
    public void onStart() {
        super.onStart();
        mPlaybackModel.registerObserver(mPlaybackObserver);
    }

    @Override
    public void onStop() {
        super.onStop();
        mPlaybackModel.unregisterObserver(mPlaybackObserver);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mDrawerController.cleanup();
        mPlaybackControls.setModel(null);
        mMetadataView.setModel(null);
    }

    @Override
    protected CarDrawerAdapter getRootAdapter() {
        return mDrawerController == null ? null : mDrawerController.getRootAdapter();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "onNewIntent(); intent: " + (intent == null ? "<< NULL >>" : intent));
        }

        setIntent(intent);
        getDrawerController().closeDrawer();
        handleIntent();
    }

    @Override
    public void onBackPressed() {
        mPlaybackFragment.closeOverflowMenu();
        super.onBackPressed();
    }

    @Override
    protected void onResumeFragments() {
        super.onResumeFragments();
        handleIntent();
    }

    private void onBrowseConnected(boolean success) {
        if (!success) {
            updateTabs(new ArrayList<>());
            mMediaSource.unsubscribeChildren(null);
            return;
        }
        mMediaSource.subscribeChildren(null, mRootItemsSubscription);
    }

    private void handleIntent() {
        switchToMode(getRequestedMediaPackageName() == null || !mContentForwardBrowseEnabled
                ? Mode.PLAYBACK
                : Mode.BROWSING);
        updateBrowseSource();
    }

    /**
     * Updates the media source being browsed. This could be necessary when the source playing
     * changes, or if the user requests to connect to a different source.
     */
    private void updateBrowseSource() {
        MediaSource mediaSource = getCurrentMediaSource();
        if (Objects.equals(mediaSource, mMediaSource)) {
            // No change, nothing to do.
            return;
        }
        if (mMediaSource != null) {
            mMediaSource.unsubscribe(mMediaSourceObserver);
            updateTabs(new ArrayList<>());
        }
        mMediaSource = mediaSource;
        if (mMediaSource != null) {
            if (Log.isLoggable(TAG, Log.INFO)) {
                Log.i(TAG, "Browsing: " + mediaSource.getName());
            }
            ComponentName component = mMediaSource.getBrowseServiceComponentName();
            MediaManager.getInstance(this).setMediaClientComponent(component);
            // If content forward browsing is disabled, then no need to subscribe to this media
            // source.
            if (mContentForwardBrowseEnabled) {
                Log.i(TAG, "Content forward is enabled: subscribing to " +
                        mMediaSource.getPackageName());
                mMediaSource.subscribe(mMediaSourceObserver);
            }
            // TODO: Show rounded icon
            mAppBarView.setAppIcon(null);
            mAppBarView.setTitle(mMediaSource.getName());
        } else {
            mAppBarView.setAppIcon(null);
            mAppBarView.setTitle(null);
        }
    }

    /**
     * @return the media source that should be browsed. If the user expressed the intention of
     * browsing something different than what is being played, we will return that. Otherwise
     * we return the souce that is playing.
     */
    private MediaSource getCurrentMediaSource() {
        String packageName = getRequestedMediaPackageName();
        return packageName != null
                ? new MediaSource(this, packageName)
                : mPlaybackModel.getMediaSource();
    }

    /**
     * @return the package name of the media source requested by the incoming {@link Intent} or
     * null if no source was indicated.
     */
    private String getRequestedMediaPackageName() {
        return getIntent() != null
                ? getIntent().getStringExtra(KEY_MEDIA_PACKAGE)
                : null;
    }

    private boolean isCurrentMediaSourcePlaying() {
        return Objects.equals(mMediaSource, mPlaybackModel.getMediaSource());
    }

    private void updateTabs(List<MediaItemMetadata> items) {
        List<MediaItemMetadata> browsableTopLevel = items.stream()
                .filter(item -> item.isBrowsable())
                .collect(Collectors.toList());
        List<MediaItemMetadata> playableTopLevel = items.stream()
                .filter(item -> item.isPlayable())
                .collect(Collectors.toList());

        // Show tabs if:
        // - We have some browesable items and we are forced to show them as tabs,
        // - or we have only browsable items on the top level and they are not too many.
        if ((!browsableTopLevel.isEmpty() && mForceBrowseTabs)
                || (playableTopLevel.isEmpty() && !browsableTopLevel.isEmpty()
                && browsableTopLevel.size() <= mMaxBrowserTabs)) {
            mDrawerBarLayout.setVisibility(View.GONE);
            mAppBarView.setVisibility(View.VISIBLE);
            mAppBarView.setItems(browsableTopLevel);
            updateBrowseFragment(browsableTopLevel.get(0));
        } else {
            mDrawerBarLayout.setVisibility(View.VISIBLE);
            mAppBarView.setVisibility(View.INVISIBLE);
            updateBrowseFragment(null);
        }
    }

    private void switchToMode(Mode mode) {
        Log.i(TAG, "Switch mode to " + mode + " (intent package: " +
                getRequestedMediaPackageName() + ")");
        mMode = mode;
        updateMetadata();
        showFragment(mode == Mode.PLAYBACK
                ? mPlaybackFragment
                : null); // Browse fragment will be loaded once we have the top level items.
    }

    private void updateBrowseFragment(MediaItemMetadata topItem) {
        if (mMode != Mode.BROWSING) {
            return;
        }
        showFragment(mContentForwardBrowseEnabled
                ? BrowseFragment.newInstance(mMediaSource, topItem)
                : null);
    }

    private void updateMetadata() {
        if (isCurrentMediaSourcePlaying()) {
            mAlbumBackground.setVisibility(View.VISIBLE);
            mBrowseScrim.setVisibility(mMode == Mode.PLAYBACK
                    ? View.GONE
                    : View.VISIBLE);
            mBrowseControlsContainer.setVisibility(mMode == Mode.PLAYBACK
                    ? View.GONE
                    : View.VISIBLE);
            MediaItemMetadata metadata = mPlaybackModel.getMetadata();
            if (Objects.equals(mCurrentMetadata, metadata)) {
                return;
            }
            mCurrentMetadata = metadata;
            Log.i(TAG, "Updating metadata: " + metadata);
            if (metadata != null) {
                metadata.getAlbumArt(this,
                        mAlbumBackground.getWidth(),
                        mAlbumBackground.getHeight(),
                        false)
                        .thenAccept(this::setBackgroundImage);
            } else {
                mAlbumBackground.setImageBitmap(null, true);
            }
        } else {
            mAlbumBackground.setVisibility(View.INVISIBLE);
            mBrowseScrim.setVisibility(View.GONE);
            mBrowseControlsContainer.setVisibility(View.GONE);
        }
    }

    private void showFragment(Fragment fragment) {
        FragmentManager manager = getSupportFragmentManager();
        if (fragment == null) {
            if (mCurrentFragment != null) {
                manager.beginTransaction()
                        .remove(mCurrentFragment)
                        .commit();
            }
        } else {
            manager.beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .commit();
        }
        mCurrentFragment = fragment;
    }

    private void setBackgroundImage(Bitmap bitmap) {
        // TODO(b/77551865): Implement image blurring once the following issue is solved:
        // b/77551557
        // bitmap = ImageUtils.blur(getContext(), bitmap, mBackgroundBlurScale,
        //        mBackgroundBlurRadius);
        mAlbumBackground.setImageBitmap(bitmap, true);
    }

    @Override
    public MediaSource getMediaSource(String packageName) {
        if (mMediaSource != null && mMediaSource.getPackageName().equals(packageName)) {
            return mMediaSource;
        }
        if (mPlaybackModel.getMediaSource() != null &&
                mPlaybackModel.getMediaSource().getPackageName().equals(packageName)) {
            return mPlaybackModel.getMediaSource();
        }
        return new MediaSource(this, packageName);
    }

    @Override
    public void onBackStackChanged() {
        // TODO: Update ActionBar
    }

    @Override
    public void onPlayableItemClicked(MediaSource mediaSource, MediaItemMetadata item) {
        mPlaybackModel.onStop();
        mediaSource.getPlaybackModel().onPlayItem(item.getId());
        setIntent(null);
        switchToMode(Mode.PLAYBACK);
    }
}
