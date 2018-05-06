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

import android.annotation.NonNull;
import android.car.Car;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.design.widget.AppBarLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.transition.Fade;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;

import com.android.car.media.common.CrossfadeImageView;
import com.android.car.media.common.MediaItemMetadata;
import com.android.car.media.common.MediaSource;
import com.android.car.media.common.MediaSourcesManager;
import com.android.car.media.common.PlaybackControls;
import com.android.car.media.common.PlaybackModel;
import com.android.car.media.drawer.MediaDrawerController;
import com.android.car.media.widgets.AppBarView;
import com.android.car.media.widgets.MetadataView;
import com.android.car.media.widgets.ViewUtils;

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
public class MediaActivity extends CarDrawerActivity implements BrowseFragment.Callbacks,
        AppSelectionFragment.Callbacks {
    private static final String TAG = "MediaActivity";

    /** Intent extra specifying the package with the MediaBrowser */
    public static final String KEY_MEDIA_PACKAGE = "media_package";
    /** Shared preferences files */
    public static final String SHARED_PREF = "com.android.car.media";
    /** Shared preference containing the last controlled source */
    public static final String LAST_MEDIA_SOURCE_SHARED_PREF_KEY = "last_media_source";

    /** Configuration (controlled from resources) */
    private boolean mContentForwardBrowseEnabled;
    private float mBackgroundBlurRadius;
    private float mBackgroundBlurScale;
    private int mFadeDuration;

    /** Models */
    private MediaDrawerController mDrawerController;
    private MediaSource mMediaSource;
    private PlaybackModel mPlaybackModel;
    private MediaSourcesManager mMediaSourcesManager;
    private SharedPreferences mSharedPreferences;

    /** Layout views */
    private AppBarView mAppBarView;
    private CrossfadeImageView mAlbumBackground;
    private PlaybackFragment mPlaybackFragment;
    private AppSelectionFragment mAppSelectionFragment;
    private AppBarLayout mDrawerBarLayout;
    private PlaybackControls mPlaybackControls;
    private MetadataView mMetadataView;
    private ViewGroup mBrowseControlsContainer;
    private Fragment mEmptyFragment;
    private ViewGroup mBrowseContainer;
    private ViewGroup mPlaybackContainer;

    /** Current state */
    private Fragment mCurrentFragment;
    private Mode mMode = Mode.BROWSING;
    private boolean mIsAppSelectorOpen;
    private MediaItemMetadata mCurrentMetadata;

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
                }

                @Override
                public void onMetadataChanged() {
                    mCurrentMetadata = null;
                    updateMetadata();
                }
            };
    private AppBarView.AppBarListener mAppBarListener = new AppBarView.AppBarListener() {
        @Override
        public void onTabSelected(MediaItemMetadata item) {
            updateBrowseFragment(item, false);
            switchToMode(Mode.BROWSING);
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
            if (mIsAppSelectorOpen) {
                closeAppSelector();
            } else {
                openAppSelector();
            }
        }
    };
    private MediaSourcesManager.Observer mMediaSourcesManagerObserver = () -> {
        mAppBarView.setAppSelection(!mMediaSourcesManager.getMediaSources().isEmpty());
        mAppSelectionFragment.refresh();
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
        mDrawerController = new MediaDrawerController(this, getDrawerController());
        getDrawerController().setRootAdapter(getRootAdapter());
        mAppBarView = findViewById(R.id.app_bar);
        mAppBarView.setListener(mAppBarListener);
        boolean forceBrowseTabs = getResources().getBoolean(R.bool.force_browse_tabs);
        mAppBarView.setVisibility(forceBrowseTabs ? View.VISIBLE : View.GONE);
        mPlaybackFragment = new PlaybackFragment();
        mAppSelectionFragment = new AppSelectionFragment();
        int fadeDuration = getResources().getInteger(R.integer.app_selector_fade_duration);
        mAppSelectionFragment.setEnterTransition(new Fade().setDuration(fadeDuration));
        mAppSelectionFragment.setExitTransition(new Fade().setDuration(fadeDuration));
        mPlaybackModel = new PlaybackModel(this);
        mMediaSourcesManager = new MediaSourcesManager(this);
        mDrawerBarLayout = findViewById(androidx.car.R.id.appbar);
        mDrawerBarLayout.setVisibility(forceBrowseTabs ? View.GONE : View.VISIBLE);
        mAlbumBackground = findViewById(R.id.media_background);
        mPlaybackControls = findViewById(R.id.browse_controls);
        mPlaybackControls.setModel(mPlaybackModel);
        mMetadataView = findViewById(R.id.browse_metadata);
        mMetadataView.setModel(mPlaybackModel);
        mBrowseControlsContainer = findViewById(R.id.browse_controls_container);
        mBrowseControlsContainer.setOnClickListener(view -> switchToMode(Mode.PLAYBACK));
        TypedValue outValue = new TypedValue();
        getResources().getValue(R.dimen.playback_background_blur_radius, outValue, true);
        mBackgroundBlurRadius = outValue.getFloat();
        getResources().getValue(R.dimen.playback_background_blur_scale, outValue, true);
        mBackgroundBlurScale = outValue.getFloat();
        mSharedPreferences = getSharedPreferences(SHARED_PREF, Context.MODE_PRIVATE);
        mFadeDuration = getResources().getInteger(
                R.integer.new_album_art_fade_in_duration);
        mEmptyFragment = new EmptyFragment();
        mBrowseContainer = findViewById(R.id.fragment_container);
        mPlaybackContainer = findViewById(R.id.playback_container);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.playback_container, mPlaybackFragment)
                .commit();
    }

    @Override
    public void onResume() {
        super.onResume();
        mPlaybackModel.registerObserver(mPlaybackObserver);
        mMediaSourcesManager.registerObserver(mMediaSourcesManagerObserver);
    }

    @Override
    public void onPause() {
        super.onPause();
        mPlaybackModel.unregisterObserver(mPlaybackObserver);
        mMediaSourcesManager.unregisterObserver(mMediaSourcesManagerObserver);
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
        Intent intent = getIntent();
        String action = intent != null ? intent.getAction() : null;

        getDrawerController().closeDrawer();

        if (Car.CAR_INTENT_ACTION_MEDIA_TEMPLATE.equals(action)) {
            // The user either wants to browse a particular media source or switch to the
            // playback UI.
            String packageName = intent.getStringExtra(KEY_MEDIA_PACKAGE);
            if (packageName != null) {
                // We were told to navigate to a particular package: we open browse for it.
                closeAppSelector();
                updateBrowseSource(new MediaSource(this, packageName));
                switchToMode(Mode.BROWSING);
                return;
            }

            // If didn't receive a package name and we are playing something: show the playback
            // UI for the playing media source.
            MediaSource mediaSource = mPlaybackModel.getMediaSource();
            if (mediaSource != null) {
                closeAppSelector();
                updateBrowseSource(mPlaybackModel.getMediaSource());
                switchToMode(Mode.PLAYBACK);
                return;
            }
        }

        // In any other case, if we were already browsing something: just close drawers/overlays
        // and display what we have.
        if (mMediaSource != null) {
            closeAppSelector();
            updateMetadata();
            return;
        }

        // If we don't have a current media source, we try with the last one we remember.
        MediaSource lastMediaSource = getLastMediaSource();
        if (lastMediaSource != null) {
            closeAppSelector();
            updateBrowseSource(lastMediaSource);
            updateMetadata();
        } else {
            // If we don't have anything from before: open the app selector.
            openAppSelector();
        }
    }

    /**
     * Updates the media source being browsed. This could be necessary when the user selects
     * a different media source.
     */
    private void updateBrowseSource(MediaSource mediaSource) {
        if (Objects.equals(mediaSource, mMediaSource)) {
            // No change, nothing to do.
            return;
        }
        if (mMediaSource != null) {
            mMediaSource.unsubscribeChildren(null);
            mMediaSource.unsubscribe(mMediaSourceObserver);
            updateTabs(new ArrayList<>());
        }
        mMediaSource = mediaSource;
        setLastMediaSource(mMediaSource);
        mAppBarView.setState(AppBarView.State.BROWSING);
        if (mMediaSource != null) {
            if (Log.isLoggable(TAG, Log.INFO)) {
                Log.i(TAG, "Browsing: " + mediaSource.getName());
            }
            ComponentName component = mMediaSource.getBrowseServiceComponentName();
            MediaManager.getInstance(this).setMediaClientComponent(component);
            // If content forward browsing is disabled, then no need to subscribe to this media
            // source.
            updateBrowseFragment(null, true);
            if (mContentForwardBrowseEnabled) {
                Log.i(TAG, "Content forward is enabled: subscribing to " +
                        mMediaSource.getPackageName());
                mMediaSource.subscribe(mMediaSourceObserver);
            }
            mAppBarView.setAppIcon(mMediaSource.getRoundPackageIcon());
            mAppBarView.setTitle(mMediaSource.getName());
        } else {
            mAppBarView.setAppIcon(null);
            mAppBarView.setTitle(null);
        }
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
        items = customizeTabs(mMediaSource, items);
        List<MediaItemMetadata> browsableTopLevel = items.stream()
                .filter(item -> item.isBrowsable())
                .collect(Collectors.toList());

        if (!browsableTopLevel.isEmpty()) {
            // If we have at least a few browsable items, we show the tabs
            mAppBarView.setItems(browsableTopLevel);
            updateBrowseFragment(browsableTopLevel.get(0), false);
        } else {
            // Otherwise, we show the top of the tree with no fabs
            mAppBarView.setItems(null);
            updateBrowseFragment(null, false);
        }
    }

    /**
     * Extension point used to customize media items displayed on the tabs.
     *
     * @param mediaSource media source these items belong to.
     * @param items items to override.
     * @return an updated list of items.
     * @deprecated This method will be removed on b/79089344
     */
    @Deprecated
    protected List<MediaItemMetadata> customizeTabs(MediaSource mediaSource,
            List<MediaItemMetadata> items) {
        return items;
    }

    private void switchToMode(Mode mode) {
        mMode = mode;
        updateMetadata();
        switch (mode) {
            case PLAYBACK:
                ViewUtils.showViewAnimated(mPlaybackContainer, mFadeDuration);
                ViewUtils.hideViewAnimated(mBrowseContainer, mFadeDuration);
                mAppBarView.setState(AppBarView.State.PLAYING);
                break;
            case BROWSING:
                ViewUtils.hideViewAnimated(mPlaybackContainer, mFadeDuration);
                ViewUtils.showViewAnimated(mBrowseContainer, mFadeDuration);
                mAppBarView.setState(AppBarView.State.BROWSING);
                break;
        }
    }

    /**
     * Updates the browse area with either a loading state, the root node content, or the
     * content of a particular media item.
     *
     * @param topItem item to display, or null to display the root node.
     * @param isLoading whether we should show the loading state.
     */
    private void updateBrowseFragment(MediaItemMetadata topItem, boolean isLoading) {
        if (isLoading) {
            mCurrentFragment = mEmptyFragment;
            mAppBarView.setActiveItem(null);
        } else if (topItem != null) {
            mCurrentFragment = BrowseFragment.newInstance(mMediaSource, topItem);
            mAppBarView.setActiveItem(topItem);
        } else {
            mCurrentFragment = BrowseFragment.newInstance(mMediaSource, null);
            mAppBarView.setActiveItem(null);
        }
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, mCurrentFragment)
                .commit();
    }

    private void updateMetadata() {
        if (isCurrentMediaSourcePlaying()) {
            if (mMode == Mode.PLAYBACK) {
                ViewUtils.hideViewAnimated(mBrowseControlsContainer, mFadeDuration);
            } else {
                ViewUtils.showViewAnimated(mBrowseControlsContainer, mFadeDuration);
            }
            MediaItemMetadata metadata = mPlaybackModel.getMetadata();
            if (Objects.equals(mCurrentMetadata, metadata)) {
                return;
            }
            mCurrentMetadata = metadata;
            mUpdateAlbumArtRunnable.run();
        } else {
            mAlbumBackground.setImageBitmap(null, true);
            ViewUtils.hideViewAnimated(mBrowseControlsContainer, mFadeDuration);
        }
    }

    /**
     * We might receive new album art before we are ready to display it. If that situation happens
     * we will retrieve and render the album art when the views are already laid out.
     */
    private Runnable mUpdateAlbumArtRunnable = new Runnable() {
        @Override
        public void run() {
            MediaItemMetadata metadata = mPlaybackModel.getMetadata();
            if (metadata != null) {
                if (mAlbumBackground.getWidth() == 0 || mAlbumBackground.getHeight() == 0) {
                    // We need to wait for the view to be measured before we can render this
                    // album art.
                    mAlbumBackground.setImageBitmap(null, false);
                    mAlbumBackground.post(this);
                } else {
                    mAlbumBackground.removeCallbacks(this);
                    metadata.getAlbumArt(MediaActivity.this,
                            mAlbumBackground.getWidth(),
                            mAlbumBackground.getHeight(),
                            false)
                            .thenAccept(bitmap -> setBackgroundImage(bitmap));
                }
            } else {
                mAlbumBackground.removeCallbacks(this);
                mAlbumBackground.setImageBitmap(null, true);
            }
        }
    };

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

    private void openAppSelector() {
        mIsAppSelectorOpen = true;
        FragmentManager manager = getSupportFragmentManager();
        mAppBarView.setState(AppBarView.State.APP_SELECTION);
        manager.beginTransaction()
                .replace(R.id.app_selection_container, mAppSelectionFragment)
                .commit();
    }

    private void closeAppSelector() {
        mIsAppSelectorOpen = false;
        FragmentManager manager = getSupportFragmentManager();
        mAppBarView.setState(AppBarView.State.BROWSING);
        manager.beginTransaction()
                .remove(mAppSelectionFragment)
                .commit();
    }

    @Override
    public List<MediaSource> getMediaSources() {
        return mMediaSourcesManager.getMediaSources()
                .stream()
                .filter(source -> source.getMediaBrowser() != null && !source.isCustom())
                .collect(Collectors.toList());
    }

    @Override
    public void onMediaSourceSelected(MediaSource mediaSource) {
        closeAppSelector();
        if (mediaSource.getMediaBrowser() != null && !mediaSource.isCustom()) {
            updateBrowseSource(mediaSource);
            switchToMode(Mode.BROWSING);
        } else {
            String packageName = mediaSource.getPackageName();
            Intent intent = getPackageManager().getLaunchIntentForPackage(packageName);
            startActivity(intent);
        }
    }

    private MediaSource getLastMediaSource() {
        String packageName = mSharedPreferences.getString(LAST_MEDIA_SOURCE_SHARED_PREF_KEY, null);
        if (packageName == null) {
            return null;
        }
        // Verify that the stored package name corresponds to a currently installed media source.
        for (MediaSource mediaSource : mMediaSourcesManager.getMediaSources()) {
            if (mediaSource.getPackageName().equals(packageName)) {
                return mediaSource;
            }
        }
        return null;
    }

    private void setLastMediaSource(@NonNull MediaSource mediaSource) {
        mSharedPreferences.edit()
                .putString(LAST_MEDIA_SOURCE_SHARED_PREF_KEY, mediaSource.getPackageName())
                .apply();
    }
}
