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

import static com.android.car.arch.common.LiveDataFunctions.distinct;
import static com.android.car.arch.common.LiveDataFunctions.nullLiveData;

import static java.util.Objects.requireNonNull;

import android.app.ActionBar;
import android.app.Application;
import android.app.PendingIntent;
import android.car.Car;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.transition.Fade;
import android.util.Log;
import android.util.Size;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.car.drawer.CarDrawerController;
import androidx.core.util.Consumer;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;

import com.android.car.apps.common.DrawerActivity;
import com.android.car.media.common.CrossfadeImageView;
import com.android.car.media.common.MediaConstants;
import com.android.car.media.common.MediaItemMetadata;
import com.android.car.media.common.browse.MediaBrowserViewModel;
import com.android.car.media.common.playback.AlbumArtLiveData;
import com.android.car.media.common.playback.PlaybackViewModel;
import com.android.car.media.common.source.MediaSource;
import com.android.car.media.common.source.MediaSourceViewModel;
import com.android.car.media.common.source.MediaSourcesLiveData;
import com.android.car.media.drawer.MediaDrawerController;
import com.android.car.media.storage.MediaSourceStorage;
import com.android.car.media.widgets.AppBarView;
import com.android.car.media.widgets.BrowsePlaybackControlBar;
import com.android.car.media.widgets.ViewUtils;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * This activity controls the UI of media. It also updates the connection status for the media app
 * by broadcast. Drawer menu is controlled by {@link MediaDrawerController}.
 */
public class MediaActivity extends DrawerActivity implements BrowseFragment.Callbacks,
        AppSelectionFragment.Callbacks, PlaybackFragment.Callbacks {
    private static final String TAG = "MediaActivity";

    /** Configuration (controlled from resources) */
    private boolean mContentForwardBrowseEnabled;
    private int mFadeDuration;

    /** Models */
    private MediaDrawerController mDrawerController;
    private PlaybackViewModel.PlaybackController mPlaybackController;
    private MediaSourceStorage mMediaSourceStorage;

    /** Layout views */
    private AppBarView mAppBarView;
    private CrossfadeImageView mAlbumBackground;
    private PlaybackFragment mPlaybackFragment;
    private BrowseFragment mSearchFragment;
    private AppSelectionFragment mAppSelectionFragment;
    private ViewGroup mBrowseControlsContainer;
    private EmptyFragment mEmptyFragment;
    private ViewGroup mBrowseContainer;
    private ViewGroup mPlaybackContainer;
    private ViewGroup mErrorContainer;
    private ErrorFragment mErrorFragment;
    private ViewGroup mSearchContainer;

    /** Current state */
    private Intent mCurrentSourcePreferences;
    private Mode mMode = Mode.BROWSING;
    private boolean mIsAppSelectorOpen;

    private Handler mHandler = new Handler(Looper.getMainLooper());

    private AppBarView.AppBarListener mAppBarListener = new AppBarView.AppBarListener() {
        @Override
        public void onTabSelected(MediaItemMetadata item) {
            showTopItem(item);
            switchToMode(Mode.BROWSING);
        }

        @Override
        public void onBack() {
            Fragment currentFragment = getCurrentFragment();
            if (currentFragment instanceof BrowseFragment) {
                BrowseFragment fragment = (BrowseFragment) currentFragment;
                fragment.navigateBack();
            }
        }

        @Override
        public void onCollapse() {
            switchToMode(Mode.BROWSING);
            onBackStackChanged();
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

        @Override
        public void onSearchSelection() {
            switchToMode(Mode.SEARCHING);
        }

        @Override
        public void onSearch(String query) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onSearch: " + query);
            }
            mSearchFragment.updateSearchQuery(query);
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
        PLAYBACK,
        /** The MediaService is in an error state */
        SERVICE_ERROR,
        /** The user is searching within a media source */
        SEARCHING
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.media_activity);

        MediaSourceViewModel mediaSourceViewModel = getMediaSourceViewModel();
        PlaybackViewModel playbackViewModel = getPlaybackViewModel();
        ViewModel localViewModel = ViewModelProviders.of(this).get(ViewModel.class);
        if (savedInstanceState == null) {
            playbackViewModel.setMediaController(mediaSourceViewModel.getMediaController());
            localViewModel.init(playbackViewModel);
        }
        mMediaSourceStorage = new MediaSourceStorage(this);

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
        mediaSourceViewModel.getSelectedMediaSource().observe(this, source -> {
            if (source == null) {
                mAppBarView.setAppIcon(null);
                mAppBarView.setTitle(null);
            } else {
                mAppBarView.setAppIcon(source.getRoundPackageIcon());
                mAppBarView.setTitle(source.getName());
            }
            if (mContentForwardBrowseEnabled) {
                mAppBarView.setContentForwardEnabled(true);
                ActionBar actionBar = requireNonNull(getActionBar());
                actionBar.hide();
            }
        });

        if (mContentForwardBrowseEnabled) {
            // If content forward browsing is disabled, then no need to observe browsed items, we
            // will use the drawer instead.
            MediaBrowserViewModel mediaBrowserViewModel = getRootBrowserViewModel();
            mediaBrowserViewModel.getBrowsedMediaItems().observe(this, futureData -> {
                if (!futureData.isLoading() && mMode != Mode.SERVICE_ERROR) {
                    updateTabs(futureData.getData());
                }
            });
            mediaBrowserViewModel.supportsSearch().observe(this,
                    mAppBarView::setSearchSupported);
        }

        mPlaybackFragment = new PlaybackFragment();
        mSearchFragment = BrowseFragment.newSearchInstance(null);
        mAppSelectionFragment = new AppSelectionFragment();
        int fadeDuration = getResources().getInteger(R.integer.app_selector_fade_duration);
        mAppSelectionFragment.setEnterTransition(new Fade().setDuration(fadeDuration));
        mAppSelectionFragment.setExitTransition(new Fade().setDuration(fadeDuration));
        mAlbumBackground = findViewById(R.id.media_background);

        BrowsePlaybackControlBar browsePlaybackControls =
                findViewById(R.id.browse_controls_container);
        browsePlaybackControls.setModel(playbackViewModel, this);

        mBrowseControlsContainer = findViewById(R.id.browse_controls_container);
        mBrowseControlsContainer.setOnClickListener(view -> switchToMode(Mode.PLAYBACK));
        TypedValue outValue = new TypedValue();
        getResources().getValue(R.dimen.playback_background_blur_radius, outValue, true);
        getResources().getValue(R.dimen.playback_background_blur_scale, outValue, true);
        mFadeDuration = getResources().getInteger(
                R.integer.new_album_art_fade_in_duration);
        mEmptyFragment = new EmptyFragment();
        mBrowseContainer = findViewById(R.id.fragment_container);
        mErrorContainer = findViewById(R.id.error_container);
        mPlaybackContainer = findViewById(R.id.playback_container);
        mSearchContainer = findViewById(R.id.search_container);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.playback_container, mPlaybackFragment)
                .commit();
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.search_container, mSearchFragment)
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

        playbackViewModel.getPlaybackState().observe(this, state -> {
            handlePlaybackState(state);
        });
    }

    private void handlePlaybackState(PlaybackStateCompat state) {
        if (state != null) {
            if (Log.isLoggable(TAG, Log.INFO)) {
                Log.i(TAG, "handlePlaybackState(); state change: " + state.getState());
            }

            if (state.getState() == PlaybackStateCompat.STATE_ERROR) {
                String message = state.getErrorMessage().toString();
                PendingIntent intent = null;
                String label = null;

                Bundle extras = state.getExtras();
                if (extras != null) {
                    intent = extras.getParcelable(MediaConstants.ERROR_RESOLUTION_ACTION_INTENT);
                    label = extras.getString(MediaConstants.ERROR_RESOLUTION_ACTION_LABEL);
                }

                if (message != null) {
                    mErrorFragment = ErrorFragment.newInstance(message, label, intent);
                    setErrorFragment(mErrorFragment);
                    switchToMode(Mode.SERVICE_ERROR);
                } else {
                    Log.e(TAG, "PlaybackState error requires an error message");
                }
            } else if (state.getState() == PlaybackStateCompat.STATE_NONE) {
                ViewUtils.hideViewAnimated(mErrorContainer, mFadeDuration);
                switchToMode(Mode.BROWSING);
            }
        }
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
                onMediaSourceSelected(new MediaSource(this, packageName));
                return;
            }

            // If we didn't receive a package name and we are playing something: show the playback
            // UI for the playing media source.
            LiveData<MediaControllerCompat> topActiveMediaController =
                    getMediaSourceViewModel().getTopActiveMediaController();
            // TODO(arnaudberry) observeOnce should be removed as things could have changed when
            // the lambda is executed...
            observeOnce(topActiveMediaController, controller -> {
                if (controller != null) {
                    closeAppSelector();
                    changeMediaSource(
                            new MediaSource(MediaActivity.this,
                                    controller.getPackageName()));
                    switchToMode(Mode.PLAYBACK);
                }
            });
        }

        // If we don't have a current media source, we try with the last one we remember...
        MediaSource mediaSource = mMediaSourceStorage.getLastMediaSource();
        if (mediaSource != null) {
            closeAppSelector();
            changeMediaSource(mediaSource);

            if (mMode != Mode.SERVICE_ERROR) {
                switchToMode(Mode.BROWSING);
            }
        } else {
            // If we don't have anything from before: open the app selector.
            openAppSelector();
        }
    }

    private boolean useContentForwardBrowse() {
        return mContentForwardBrowseEnabled;
    }

    /**
     * Sets the media source being browsed.
     *
     * @param mediaSource the media source we are going to try to browse
     */
    private void changeMediaSource(@Nullable MediaSource mediaSource) {
        MediaSourceViewModel mediaSourceViewModel = getMediaSourceViewModel();
        if (Objects.equals(mediaSource, mediaSourceViewModel.getSelectedMediaSource().getValue())) {
            // No change, nothing to do.
            return;
        }

        MediaControllerCompat controller = mediaSourceViewModel.getMediaController().getValue();
        if (controller != null) {
            MediaControllerCompat.TransportControls controls = controller.getTransportControls();
            if (controls != null) {
                controls.pause();
            }
        }

        mediaSourceViewModel.setSelectedMediaSource(mediaSource);
        mMediaSourceStorage.setLastMediaSource(mediaSource);
        if (mediaSource != null) {
            if (Log.isLoggable(TAG, Log.INFO)) {
                Log.i(TAG, "Browsing: " + mediaSource.getName());
            }
            // Make the drawer display browse information of the selected source
            ComponentName component = mediaSource.getBrowseServiceComponentName();
            String packageName = mediaSource.getPackageName();
            MediaManager.getInstance(this).setMediaClientComponent(component);
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
    private void updateTabs(List<MediaItemMetadata> items) {
        if (items == null || items.isEmpty()) {
            mAppBarView.setActiveItem(null);
            mAppBarView.setItems(null);
            setCurrentFragment(mEmptyFragment);
            return;
        }

        List<MediaItemMetadata> browsableTopLevel = items.stream()
                .filter(MediaItemMetadata::isBrowsable)
                .collect(Collectors.toList());

        mAppBarView.setItems(browsableTopLevel);
        showTopItem(browsableTopLevel.isEmpty() ? null : browsableTopLevel.get(0));
    }

    private void showTopItem(@Nullable MediaItemMetadata topItem) {
        setCurrentFragment(BrowseFragment.newInstance(topItem));
        mAppBarView.setActiveItem(topItem);
    }

    private void setErrorFragment(Fragment fragment) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.error_container, fragment)
                .commitAllowingStateLoss();
    }

    private void setCurrentFragment(Fragment fragment) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commitAllowingStateLoss();
    }

    @Nullable
    private Fragment getCurrentFragment() {
        return getSupportFragmentManager().findFragmentById(R.id.fragment_container);
    }

    private void switchToMode(Mode mode) {
        // If content forward is not enabled, then we always show the playback UI (browse will be
        // done in the drawer)
        mMode = useContentForwardBrowse() ? mode : Mode.PLAYBACK;
        updateMetadata();

        if (Log.isLoggable(TAG, Log.INFO)) {
            Log.i(TAG, "switchToMode(); mode: " + mode);
        }

        switch (mMode) {
            case PLAYBACK:
                ViewUtils.hideViewAnimated(mErrorContainer, mFadeDuration);
                ViewUtils.showViewAnimated(mPlaybackContainer, mFadeDuration);
                ViewUtils.hideViewAnimated(mBrowseContainer, mFadeDuration);
                ViewUtils.hideViewAnimated(mSearchContainer, mFadeDuration);
                mAppBarView.setState(AppBarView.State.PLAYING);
                break;
            case BROWSING:
                ViewUtils.hideViewAnimated(mErrorContainer, mFadeDuration);
                ViewUtils.hideViewAnimated(mPlaybackContainer, mFadeDuration);
                ViewUtils.showViewAnimated(mBrowseContainer, mFadeDuration);
                ViewUtils.hideViewAnimated(mSearchContainer, mFadeDuration);
                mAppBarView.setState(AppBarView.State.BROWSING);
                break;
            case SERVICE_ERROR:
                ViewUtils.showViewAnimated(mErrorContainer, mFadeDuration);
                ViewUtils.hideViewAnimated(mPlaybackContainer, mFadeDuration);
                ViewUtils.hideViewAnimated(mBrowseContainer, mFadeDuration);
                ViewUtils.hideViewAnimated(mSearchContainer, mFadeDuration);
                mAppBarView.setState(AppBarView.State.EMPTY);
                break;
            case SEARCHING:
                ViewUtils.hideViewAnimated(mErrorContainer, mFadeDuration);
                ViewUtils.hideViewAnimated(mPlaybackContainer, mFadeDuration);
                ViewUtils.hideViewAnimated(mBrowseContainer, mFadeDuration);
                ViewUtils.showViewAnimated(mSearchContainer, mFadeDuration);
                mAppBarView.setState(AppBarView.State.SEARCHING);
                break;
        }
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
        Fragment currentFragment = getCurrentFragment();
        if (currentFragment instanceof BrowseFragment) {
            BrowseFragment fragment = (BrowseFragment) currentFragment;
            mAppBarView.setNavIconVisible(fragment.isBackEnabled());
        }
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
    public void onMediaSourceSelected(@NonNull MediaSource mediaSource) {
        closeAppSelector();
        if (mediaSource.isBrowsable() && !mediaSource.isCustom()) {
            changeMediaSource(mediaSource);
            switchToMode(Mode.BROWSING);
        } else {
            mMediaSourceStorage.setLastMediaSource(mediaSource);
            String packageName = mediaSource.getPackageName();
            Intent intent = getPackageManager().getLaunchIntentForPackage(packageName);
            startActivity(intent);
        }
    }


    public MediaSourceViewModel getMediaSourceViewModel() {
        return ViewModelProviders.of(this).get(MediaSourceViewModel.class);
    }

    public PlaybackViewModel getPlaybackViewModel() {
        return ViewModelProviders.of(this).get(PlaybackViewModel.class);
    }

    private MediaBrowserViewModel getRootBrowserViewModel() {
        return MediaBrowserViewModel.Factory.getInstanceForBrowseRoot(ViewModelProviders.of(this));
    }

    public ViewModel getInnerViewModel() {
        return ViewModelProviders.of(this).get(ViewModel.class);
    }

    @Override
    public void onQueueButtonClicked() {
        if (useContentForwardBrowse()) {
            mPlaybackFragment.toggleQueueVisibility();
        } else {
            mDrawerController.showPlayQueue();
        }
    }

    public static class ViewModel extends AndroidViewModel {

        private LiveData<Bitmap> mAlbumArt;
        private MutableLiveData<Size> mAlbumArtSize = new MutableLiveData<>();
        private PlaybackViewModel mPlaybackViewModel;

        public ViewModel(@NonNull Application application) {
            super(application);
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
    }
}
