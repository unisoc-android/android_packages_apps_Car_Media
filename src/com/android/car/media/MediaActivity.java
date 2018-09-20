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

import static androidx.lifecycle.Transformations.switchMap;

import static com.android.car.arch.common.LiveDataFunctions.combine;
import static com.android.car.arch.common.LiveDataFunctions.distinct;
import static com.android.car.arch.common.LiveDataFunctions.nullLiveData;

import static java.util.Objects.requireNonNull;

import android.app.Application;
import android.car.Car;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.media.session.MediaControllerCompat;
import android.transition.Fade;
import android.util.Log;
import android.util.Size;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.car.drawer.CarDrawerController;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;

import com.android.car.apps.common.DrawerActivity;
import com.android.car.arch.common.preference.StringPreferenceLiveData;
import com.android.car.arch.common.switching.SwitchingLiveData;
import com.android.car.media.common.CrossfadeImageView;
import com.android.car.media.common.MediaItemMetadata;
import com.android.car.media.common.MediaSource;
import com.android.car.media.common.PlaybackControls;
import com.android.car.media.common.playback.AlbumArtLiveData;
import com.android.car.media.common.playback.PlaybackViewModel;
import com.android.car.media.common.source.MediaSourceViewModel;
import com.android.car.media.common.source.SimpleMediaSource;
import com.android.car.media.drawer.MediaDrawerController;
import com.android.car.media.widgets.AppBarView;
import com.android.car.media.widgets.MetadataView;
import com.android.car.media.widgets.ViewUtils;

import com.google.android.material.appbar.AppBarLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * This activity controls the UI of media. It also updates the connection status for the media app
 * by broadcast. Drawer menu is controlled by {@link MediaDrawerController}.
 */
public class MediaActivity extends DrawerActivity implements BrowseFragment.Callbacks,
        AppSelectionFragment.Callbacks, PlaybackFragment.Callbacks {
    private static final String TAG = "MediaActivity";

    /** Shared preferences files */
    public static final String SHARED_PREF = "com.android.car.media";
    /** Shared preference containing the last controlled source */
    public static final String LAST_MEDIA_SOURCE_SHARED_PREF_KEY = "last_media_source";

    /** Configuration (controlled from resources) */
    private boolean mContentForwardBrowseEnabled;
    private int mFadeDuration;

    /** Models */
    private MediaDrawerController mDrawerController;
    @Nullable
    private MediaSource mMediaSource;
    private PlaybackViewModel.PlaybackController mPlaybackController;

    /** Layout views */
    private AppBarView mAppBarView;
    private CrossfadeImageView mAlbumBackground;
    private PlaybackFragment mPlaybackFragment;
    private AppSelectionFragment mAppSelectionFragment;
    private ViewGroup mBrowseControlsContainer;
    private EmptyFragment mEmptyFragment;
    private ViewGroup mBrowseContainer;
    private ViewGroup mPlaybackContainer;

    /** Current state */
    private Fragment mCurrentFragment;
    private Intent mCurrentSourcePreferences;
    private Mode mMode = Mode.BROWSING;
    private boolean mIsAppSelectorOpen;

    private Handler mHandler = new Handler(Looper.getMainLooper());

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
    private MediaSource.ItemsSubscription mItemsSubscription =
            new MediaSource.ItemsSubscription() {
                @Override
                public void onChildrenLoaded(MediaSource mediaSource, String parentId,
                        List<MediaItemMetadata> items) {
                    if (mediaSource == mMediaSource) {
                        updateTabs(items, MediaActivity.this.mMediaSource);
                    } else {
                        Log.w(TAG, "Received items for a wrong source: " +
                                mediaSource.getPackageName());
                    }
                }
            };
    private AppBarView.AppBarListener mAppBarListener = new AppBarView.AppBarListener() {
        @Override
        public void onTabSelected(MediaItemMetadata item) {
            updateBrowseFragment(BrowseState.LOADED, item, mMediaSource);
            switchToMode(Mode.BROWSING);
        }

        @Override
        public void onBack() {
            if (mCurrentFragment instanceof BrowseFragment) {
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
            Log.d(TAG, "onAppSelection clicked");
            if (mIsAppSelectorOpen) {
                closeAppSelector();
            } else {
                openAppSelector();
            }
        }

        @Override
        public void onSettingsSelection() {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onSettingsSelection");
            }
            try {
                if (mCurrentSourcePreferences != null) {
                    startActivity(mCurrentSourcePreferences);
                }
            } catch (ActivityNotFoundException e) {
                if (Log.isLoggable(TAG, Log.ERROR)) {
                    Log.e(TAG, "onSettingsSelection " + e);
                }
            }
        }
    };
    private DrawerLayout.DrawerListener mDrawerListener = new DrawerLayout.DrawerListener() {
        @Override
        public void onDrawerSlide(@androidx.annotation.NonNull View view, float v) {
        }

        @Override
        public void onDrawerOpened(@androidx.annotation.NonNull View view) {
            closeAppSelector();
        }

        @Override
        public void onDrawerClosed(@androidx.annotation.NonNull View view) {
        }

        @Override
        public void onDrawerStateChanged(int i) {
        }
    };

    /**
     * Possible modes of the application UI
     */
    private enum Mode {
        /** The user is browsing a media source */
        BROWSING,
        /** The user is interacting with the full screen playback UI */
        PLAYBACK
    }

    /**
     * Possible states of the application UI
     */
    public enum BrowseState {
        /** There is no content to show */
        EMPTY,
        /** We are still in the process of obtaining data */
        LOADING,
        /** Data has been loaded */
        LOADED,
        /** The content can't be shown due an error */
        ERROR
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.media_activity);

        MediaSourceViewModel mediaSourceViewModel = getMediaSourceViewModel();
        PlaybackViewModel playbackViewModel = getPlaybackViewModel();
        playbackViewModel.setMediaController(mediaSourceViewModel.getMediaController());
        ViewModel localViewModel = ViewModelProviders.of(this).get(ViewModel.class);
        localViewModel.init(playbackViewModel);

        mContentForwardBrowseEnabled = getResources()
                .getBoolean(R.bool.forward_content_browse_enabled);
        CarDrawerController drawerController = requireNonNull(getDrawerController());
        mDrawerController = new MediaDrawerController(this, drawerController);
        drawerController.setRootAdapter(mDrawerController.getRootAdapter());
        drawerController.addDrawerListener(mDrawerListener);
        if (mContentForwardBrowseEnabled) {
            requireNonNull(getActionBar()).hide();
        }

        mAppBarView = findViewById(R.id.app_bar);
        mAppBarView.setListener(mAppBarListener);
        mAppBarView.setContentForwardEnabled(mContentForwardBrowseEnabled);
        mediaSourceViewModel.hasMediaSources().observe(this, mAppBarView::setAppSelection);
        mediaSourceViewModel.getSelectedMediaSource().observe(this, source -> {
            if (source == null) {
                mAppBarView.setAppIcon(null);
                mAppBarView.setTitle(null);
            } else {
                mAppBarView.setAppIcon(source.getRoundPackageIcon());
                mAppBarView.setTitle(source.getName());
            }
        });

        mPlaybackFragment = new PlaybackFragment();
        mAppSelectionFragment = new AppSelectionFragment();
        int fadeDuration = getResources().getInteger(R.integer.app_selector_fade_duration);
        mAppSelectionFragment.setEnterTransition(new Fade().setDuration(fadeDuration));
        mAppSelectionFragment.setExitTransition(new Fade().setDuration(fadeDuration));
        mAlbumBackground = findViewById(R.id.media_background);
        PlaybackControls playbackControls = findViewById(R.id.browse_controls);
        playbackControls.setModel(playbackViewModel, this);
        MetadataView metadataView = findViewById(R.id.browse_metadata);
        metadataView.setModel(playbackViewModel, this);
        mBrowseControlsContainer = findViewById(R.id.browse_controls_container);
        mBrowseControlsContainer.setOnClickListener(view -> switchToMode(Mode.PLAYBACK));
        TypedValue outValue = new TypedValue();
        getResources().getValue(R.dimen.playback_background_blur_radius, outValue, true);
        getResources().getValue(R.dimen.playback_background_blur_scale, outValue, true);
        mFadeDuration = getResources().getInteger(
                R.integer.new_album_art_fade_in_duration);
        mEmptyFragment = new EmptyFragment();
        mBrowseContainer = findViewById(R.id.fragment_container);
        mPlaybackContainer = findViewById(R.id.playback_container);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.playback_container, mPlaybackFragment)
                .commit();

        handleIntent();
        playbackViewModel.getPlaybackController().observe(this,
                playbackController -> {
                    if (playbackController != null) playbackController.prepare();
                    mPlaybackController = playbackController;
                });

        mAlbumBackground.addOnLayoutChangeListener(
                (view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                        localViewModel.setAlbumArtSize(
                                mAlbumBackground.getWidth(), mAlbumBackground.getHeight()));
        localViewModel.getAlbumArt().observe(this, this::setBackgroundImage);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mDrawerController.cleanup();
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

    private void onBrowseConnected(boolean success) {
        // mMediaSource shouldn't be null if we've just connected to its MediaBrowser
        requireNonNull(mMediaSource);
        if (!success) {
            updateTabs(null, mMediaSource);
            mMediaSource.unsubscribeChildren(null, mItemsSubscription);
            mMediaSource.unsubscribe(mMediaSourceObserver);
            updateBrowseFragment(BrowseState.ERROR, null, mMediaSource);
            return;
        }
        mMediaSource.subscribeChildren(null, mItemsSubscription);
    }

    private void handleIntent() {
        Intent intent = getIntent();
        String action = intent != null ? intent.getAction() : null;

        requireNonNull(getDrawerController()).closeDrawer();

        if (Car.CAR_INTENT_ACTION_MEDIA_TEMPLATE.equals(action)) {
            // The user either wants to browse a particular media source or switch to the
            // playback UI.
            String packageName = intent.getStringExtra(Car.CAR_EXTRA_MEDIA_PACKAGE);
            if (packageName != null) {
                // We were told to navigate to a particular package: we open browse for it.
                closeAppSelector();
                changeMediaSource(new SimpleMediaSource(this, packageName));
                switchToMode(Mode.BROWSING);
                return;
            }

            // If we didn't receive a package name and we are playing something: show the playback
            // UI for the playing media source.
            LiveData<MediaControllerCompat> topActiveMediaController =
                    getMediaSourceViewModel().getTopActiveMediaController();
            observeOnce(topActiveMediaController, controller -> {
                if (controller != null) {
                    closeAppSelector();
                    changeMediaSource(new SimpleMediaSource(MediaActivity.this,
                            controller.getPackageName()));
                    switchToMode(Mode.PLAYBACK);
                }
            });
        }

        // If we don't have a current media source, we try with the last one we remember.
        observeOnce(getInnerViewModel().getLastMediaSource(), lastMediaSource -> {
            if (lastMediaSource != null) {
                closeAppSelector();
                changeMediaSource(lastMediaSource);
                switchToMode(Mode.BROWSING);
            } else {
                // If we don't have anything from before: open the app selector.
                openAppSelector();
            }
        });
    }

    /**
     * Sets the media source being browsed.
     *
     * @param mediaSource the media source we are going to try to browse
     */
    private void changeMediaSource(@Nullable SimpleMediaSource mediaSource) {
        MediaSourceViewModel mediaSourceViewModel = getMediaSourceViewModel();
        if (Objects.equals(mediaSource, mediaSourceViewModel.getSelectedMediaSource().getValue())) {
            // No change, nothing to do.
            return;
        }
        mediaSourceViewModel.setSelectedMediaSource(mediaSource);
        if (mMediaSource != null) {
            mMediaSource.unsubscribeChildren(null, mItemsSubscription);
            mMediaSource.unsubscribe(mMediaSourceObserver);
            updateTabs(new ArrayList<>(), mMediaSource);
        }
        mMediaSource = mediaSource == null ? null : mediaSource.toMediaSource();
        getInnerViewModel().setLastMediaSource(mediaSource);
        if (mMediaSource != null) {
            if (Log.isLoggable(TAG, Log.INFO)) {
                Log.i(TAG, "Browsing: " + mMediaSource.getName());
            }
            // Make the drawer display browse information of the selected source
            ComponentName component = mMediaSource.getBrowseServiceComponentName();
            String packageName = mMediaSource.getPackageName();
            MediaManager.getInstance(this).setMediaClientComponent(component);
            // If content forward browsing is disabled, then no need to subscribe to this media
            // source, we will use the drawer instead.
            if (mContentForwardBrowseEnabled) {
                Log.i(TAG, "Content forward is enabled: subscribing to " + packageName);
                updateBrowseFragment(BrowseState.LOADING, null, mMediaSource);
                mMediaSource.subscribe(mMediaSourceObserver);
            }
            updateSourcePreferences(packageName);
        } else {
            updateSourcePreferences(null);
        }
    }

    private void updateSourcePreferences(@Nullable String packageName) {
        mCurrentSourcePreferences = null;
        if (packageName != null) {
            Intent prefsIntent = new Intent(Intent.ACTION_APPLICATION_PREFERENCES);
            prefsIntent.setPackage(packageName);
            ResolveInfo info = getPackageManager().resolveActivity(prefsIntent, 0);
            if (info != null) {
                mCurrentSourcePreferences = new Intent(prefsIntent.getAction())
                        .setClassName(info.activityInfo.packageName, info.activityInfo.name);
            }
        }
        mAppBarView.showSettings(mCurrentSourcePreferences != null);
    }

    /**
     * Updates the tabs displayed on the app bar, based on the top level items on the browse tree.
     * If there is at least one browsable item, we show the browse content of that node. If there
     * are only playable items, then we show those items. If there are not items at all, we show the
     * empty message. If we receive null, we show the error message.
     *
     * @param items top level items, or null if there was an error trying load those items.
     */
    private void updateTabs(List<MediaItemMetadata> items, @NonNull MediaSource mediaSource) {
        if (items == null || items.isEmpty()) {
            mAppBarView.setItems(null);
            updateBrowseFragment(items == null ? BrowseState.ERROR : BrowseState.EMPTY, null,
                    mediaSource);
            return;
        }

        items = customizeTabs(mediaSource, items);
        List<MediaItemMetadata> browsableTopLevel = items.stream()
                .filter(MediaItemMetadata::isBrowsable)
                .collect(Collectors.toList());

        if (!browsableTopLevel.isEmpty()) {
            // If we have at least a few browsable items, we show the tabs
            mAppBarView.setItems(browsableTopLevel);
            updateBrowseFragment(BrowseState.LOADED, browsableTopLevel.get(0), mediaSource);
        } else {
            // Otherwise, we show the top of the tree with no fabs
            mAppBarView.setItems(null);
            updateBrowseFragment(BrowseState.LOADED, null, mediaSource);
        }
    }

    /**
     * Extension point used to customize media items displayed on the tabs.
     *
     * @param mediaSource media source these items belong to.
     * @param items       items to override.
     * @return an updated list of items.
     * @deprecated This method will be removed on b/79089344
     */
    @Deprecated
    protected List<MediaItemMetadata> customizeTabs(MediaSource mediaSource,
            List<MediaItemMetadata> items) {
        return items;
    }

    private void switchToMode(Mode mode) {
        // If content forward is not enable, then we always show the playback UI (browse will be
        // done in the drawer)
        mMode = mContentForwardBrowseEnabled ? mode : Mode.PLAYBACK;
        updateMetadata();
        switch (mMode) {
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
     * Updates the browse area with either a loading state, the root node content, or the content of
     * a particular media item.
     *
     * @param state   state in the process of loading browse information.
     * @param topItem if state == IDLE, this will contain the item to display, or null to display
     *                the root node.
     */
    private void updateBrowseFragment(BrowseState state, MediaItemMetadata topItem,
            @NonNull MediaSource mediaSource) {
        switch (state) {
            case LOADED:
                if (topItem != null) {
                    mCurrentFragment = BrowseFragment.newInstance(topItem);
                    mAppBarView.setActiveItem(topItem);
                } else {
                    mCurrentFragment = BrowseFragment.newInstance(null);
                    mAppBarView.setActiveItem(null);
                }
                break;
            case EMPTY:
            case LOADING:
            case ERROR:
                mCurrentFragment = mEmptyFragment;
                mEmptyFragment.setState(state, mediaSource);
                mAppBarView.setActiveItem(null);
                break;
        }
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, mCurrentFragment)
                .commitAllowingStateLoss();
    }

    private void updateMetadata() {
        if (mMode == Mode.PLAYBACK) {
            ViewUtils.hideViewAnimated(mBrowseControlsContainer, mFadeDuration);
            ViewUtils.showViewAnimated(mAlbumBackground, mFadeDuration);
        } else {
            ViewUtils.showViewAnimated(mBrowseControlsContainer, mFadeDuration);
            ViewUtils.hideViewAnimated(mAlbumBackground, mFadeDuration);
        }
    }

    private void setBackgroundImage(Bitmap bitmap) {
        // TODO(b/77551865): Implement image blurring once the following issue is solved:
        // b/77551557
        // bitmap = ImageUtils.blur(getContext(), bitmap, mBackgroundBlurScale,
        //        mBackgroundBlurRadius);
        mAlbumBackground.setImageBitmap(bitmap, bitmap != null);
    }

    @Override
    public void onBackStackChanged() {
        // TODO: Update ActionBar
    }

    @Override
    public void onPlayableItemClicked(MediaItemMetadata item) {
        mPlaybackController.stop();
        mPlaybackController.playItem(item.getId());
        setIntent(null);
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
        mAppBarView.setState(mMode == Mode.PLAYBACK ? AppBarView.State.PLAYING
                : AppBarView.State.BROWSING);
        manager.beginTransaction()
                .remove(mAppSelectionFragment)
                .commit();
    }

    private <T> void observeOnce(@NonNull LiveData<T> data, Consumer<T> consumer) {
        data.observe(this, new Observer<T>() {

            private boolean hasObserved = false;

            @Override
            public void onChanged(T value) {
                if (!hasObserved) {
                    consumer.accept(value);
                }
                hasObserved = true;
                mHandler.post(() -> data.removeObserver(this));
            }
        });
    }

    @Override
    public void onMediaSourceSelected(@NonNull SimpleMediaSource mediaSource) {
        closeAppSelector();
        if (mediaSource.isBrowsable() && !mediaSource.isCustom()) {
            changeMediaSource(mediaSource);
            switchToMode(Mode.BROWSING);
        } else {
            String packageName = mediaSource.getPackageName();
            Intent intent = getPackageManager().getLaunchIntentForPackage(packageName);
            startActivity(intent);
        }
    }

    public PlaybackViewModel getPlaybackViewModel() {
        return ViewModelProviders.of(this).get(PlaybackViewModel.class);
    }

    public MediaSourceViewModel getMediaSourceViewModel() {
        return ViewModelProviders.of(this).get(MediaSourceViewModel.class);
    }

    public ViewModel getInnerViewModel() {
        ViewModel viewModel = ViewModelProviders.of(this).get(ViewModel.class);
        viewModel.setMediaSources(getMediaSourceViewModel().getMediaSources());
        return viewModel;
    }

    @Override
    public void onQueueButtonClicked() {
        if (mContentForwardBrowseEnabled) {
            mPlaybackFragment.toggleQueueVisibility();
        } else {
            mDrawerController.showPlayQueue();
        }
    }

    public static class ViewModel extends AndroidViewModel {

        private SharedPreferences mSharedPreferences;

        private final SwitchingLiveData<List<SimpleMediaSource>> mMediaSources =
                SwitchingLiveData.newInstance();
        private final LiveData<String> mLastMediaSourcePackageName;
        private final LiveData<SimpleMediaSource> mLastMediaSource;

        private LiveData<Bitmap> mAlbumArt;
        private MutableLiveData<Size> mAlbumArtSize = new MutableLiveData<>();
        private PlaybackViewModel mPlaybackViewModel;

        public ViewModel(@NonNull Application application) {
            super(application);
            mSharedPreferences = application.getSharedPreferences(SHARED_PREF,
                    Context.MODE_PRIVATE);
            mLastMediaSourcePackageName = new StringPreferenceLiveData(mSharedPreferences,
                    LAST_MEDIA_SOURCE_SHARED_PREF_KEY, null);
            mLastMediaSource = combine(mMediaSources.asLiveData(), mLastMediaSourcePackageName,
                    (sources, packageName) -> {
                        if (packageName == null) {
                            return null;
                        }
                        // Verify that the stored package name corresponds to a currently
                        // installed media source.
                        for (SimpleMediaSource mediaSource : sources) {
                            if (mediaSource.getPackageName().equals(packageName)) {
                                return mediaSource;
                            }
                        }
                        return null;
                    });
        }

        void init(@NonNull PlaybackViewModel playbackViewModel) {
            if (mPlaybackViewModel == playbackViewModel) {
                return;
            }
            mPlaybackViewModel = playbackViewModel;

            mAlbumArt = switchMap(distinct(mAlbumArtSize), size -> {
                if (size == null || size.getHeight() == 0 || size.getWidth() == 0) {
                    return nullLiveData();
                } else {
                    return AlbumArtLiveData.getAlbumArt(getApplication(),
                            size.getWidth(), size.getHeight(), false,
                            playbackViewModel.getMetadata());
                }
            });
        }

        void setAlbumArtSize(int width, int height) {
            mAlbumArtSize.setValue(new Size(width, height));
        }

        LiveData<Bitmap> getAlbumArt() {
            return mAlbumArt;
        }

        void setMediaSources(LiveData<List<SimpleMediaSource>> mediaSources) {
            mMediaSources.setSource(mediaSources);
        }

        LiveData<SimpleMediaSource> getLastMediaSource() {
            return mLastMediaSource;
        }

        void setLastMediaSource(SimpleMediaSource mediaSource) {
            mSharedPreferences.edit()
                    .putString(LAST_MEDIA_SOURCE_SHARED_PREF_KEY, mediaSource.getPackageName())
                    .apply();
        }
    }
}
