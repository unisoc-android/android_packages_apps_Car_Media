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
import static com.android.car.arch.common.LiveDataFunctions.pair;

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
import androidx.core.util.Pair;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProviders;

import com.android.car.apps.common.DrawerActivity;
import com.android.car.media.common.AppSelectionFragment;
import com.android.car.media.common.CrossfadeImageView;
import com.android.car.media.common.MediaAppSelectorWidget;
import com.android.car.media.common.MediaConstants;
import com.android.car.media.common.MediaItemMetadata;
import com.android.car.media.common.browse.MediaBrowserViewModel;
import com.android.car.media.common.playback.AlbumArtLiveData;
import com.android.car.media.common.playback.PlaybackViewModel;
import com.android.car.media.common.source.MediaSource;
import com.android.car.media.common.source.MediaSourceViewModel;
import com.android.car.media.drawer.MediaDrawerController;
import com.android.car.media.widgets.AppBarView;
import com.android.car.media.widgets.BrowsePlaybackControlBar;
import com.android.car.media.widgets.ViewUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * This activity controls the UI of media. It also updates the connection status for the media app
 * by broadcast. Drawer menu is controlled by {@link MediaDrawerController}.
 */
public class MediaActivity extends DrawerActivity implements BrowseFragment.Callbacks,
        PlaybackFragment.Callbacks {
    private static final String TAG = "MediaActivity";

    /** Configuration (controlled from resources) */
    private int mFadeDuration;

    /** Models */
    private MediaDrawerController mDrawerController;
    private PlaybackViewModel.PlaybackController mPlaybackController;

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

    private Handler mHandler = new Handler(Looper.getMainLooper());

    private AppBarView.AppBarListener mAppBarListener = new AppBarView.AppBarListener() {
        @Override
        public void onTabSelected(MediaItemMetadata item) {
            showTopItem(item);
            getInnerViewModel().setMode(Mode.BROWSING);
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
            getInnerViewModel().setMode(Mode.BROWSING);
            onBackStackChanged();
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
            getInnerViewModel().setMode(Mode.SEARCHING);
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
        /** The user is searching within a media source */
        SEARCHING
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.media_activity);

        boolean contentForwardBrowseEnabled = getResources()
                .getBoolean(R.bool.forward_content_browse_enabled);

        MediaSourceViewModel mediaSourceViewModel = getMediaSourceViewModel();
        PlaybackViewModel playbackViewModel = getPlaybackViewModel();
        ViewModel localViewModel = ViewModelProviders.of(this).get(ViewModel.class);
        if (savedInstanceState == null) {
            playbackViewModel.setMediaController(mediaSourceViewModel.getMediaController());
            localViewModel.init(playbackViewModel, contentForwardBrowseEnabled);
        }

        CarDrawerController drawerController = requireNonNull(getDrawerController());
        mDrawerController = new MediaDrawerController(this, drawerController);
        drawerController.setRootAdapter(mDrawerController.getRootAdapter());
        drawerController.addDrawerListener(mDrawerListener);
        if (contentForwardBrowseEnabled) {
            requireNonNull(getActionBar()).hide();
        }

        mAppBarView = findViewById(R.id.app_bar);
        mAppBarView.setListener(mAppBarListener);
        mAppBarView.setContentForwardEnabled(contentForwardBrowseEnabled);
        mediaSourceViewModel.getPrimaryMediaSource().observe(this, source -> {
            if (contentForwardBrowseEnabled) {
                mAppBarView.setContentForwardEnabled(true);
                updateTabs(null);
                ActionBar actionBar = requireNonNull(getActionBar());
                actionBar.hide();
                getInnerViewModel().setMode(Mode.BROWSING);
            }
            onMediaSourceChanged(source);
        });

        MediaAppSelectorWidget appSelector = findViewById(R.id.app_switch_container);
        appSelector.setFragmentActivity(this);

        mEmptyFragment = new EmptyFragment();
        if (contentForwardBrowseEnabled) {
            // If content forward browsing is disabled, then no need to observe browsed items, we
            // will use the drawer instead.
            MediaBrowserViewModel mediaBrowserViewModel = getRootBrowserViewModel();
            mediaBrowserViewModel.getBrowseState().observe(this,
                    browseState -> mEmptyFragment.setState(browseState,
                            mediaSourceViewModel.getPrimaryMediaSource().getValue()));
            mediaBrowserViewModel.getBrowsedMediaItems().observe(this, futureData -> {
                if (!futureData.isLoading()) {
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
        mBrowseControlsContainer.setOnClickListener(
                view -> getInnerViewModel().setMode(Mode.PLAYBACK));
        TypedValue outValue = new TypedValue();
        getResources().getValue(R.dimen.playback_background_blur_radius, outValue, true);
        getResources().getValue(R.dimen.playback_background_blur_scale, outValue, true);
        mFadeDuration = getResources().getInteger(
                R.integer.new_album_art_fade_in_duration);
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

        localViewModel.getModeAndErrorState().observe(this, pair -> {
            handleModeAndErrorState(pair.first, pair.second);
        });
    }

    private void handlePlaybackState(PlaybackStateCompat state) {
        if (state == null) {
            return;
        }

        if (Log.isLoggable(TAG, Log.INFO)) {
            Log.i(TAG, "handlePlaybackState(); state change: " + state.getState());
        }

        if (state.getState() == PlaybackStateCompat.STATE_ERROR) {
            String message;
            if (state.getErrorMessage() == null) {
                message = getString(R.string.default_error_message);
            } else {
                message = state.getErrorMessage().toString();
            }

            PendingIntent intent = null;
            String label = null;

            Bundle extras = state.getExtras();
            if (extras != null) {
                intent = extras.getParcelable(MediaConstants.ERROR_RESOLUTION_ACTION_INTENT);
                label = extras.getString(MediaConstants.ERROR_RESOLUTION_ACTION_LABEL);
            }

            mErrorFragment = ErrorFragment.newInstance(message, label, intent);
            setErrorFragment(mErrorFragment);
            getInnerViewModel().setErrorState(true);
        } else {
            getInnerViewModel().setErrorState(false);
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
        requireNonNull(getDrawerController()).closeDrawer();
        MediaSource mediaSource = getMediaSourceViewModel().getPrimaryMediaSource().getValue();
        if (mediaSource == null) {
            openAppSelector();
        }
    }

    /**
     * Sets the media source being browsed.
     *
     * @param mediaSource the new media source we are going to try to browse
     */
    private void onMediaSourceChanged(@Nullable MediaSource mediaSource) {
        if (mediaSource != null) {
            if (Log.isLoggable(TAG, Log.INFO)) {
                Log.i(TAG, "Browsing: " + mediaSource.getName());
            }
            // Make the drawer display browse information of the selected source
            ComponentName component = mediaSource.getBrowseServiceComponentName();
            String packageName = mediaSource.getPackageName();
            MediaManager.getInstance(this).setMediaClientComponent(component);
            updateSourcePreferences(packageName);

            // Always go through the trampoline activity to keep all the dispatching logic there.
            startActivity(new Intent(Car.CAR_INTENT_ACTION_MEDIA_TEMPLATE));
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

    private void handleModeAndErrorState(Mode mode, Boolean isErrorMode) {
        if (Log.isLoggable(TAG, Log.INFO)) {
            Log.i(TAG, "switchToMode(); mode: " + mode + " errorState: " + isErrorMode);
        }

        if (isErrorMode) {
            ViewUtils.showViewAnimated(mErrorContainer, mFadeDuration);
            ViewUtils.hideViewAnimated(mPlaybackContainer, mFadeDuration);
            ViewUtils.hideViewAnimated(mBrowseContainer, mFadeDuration);
            ViewUtils.hideViewAnimated(mSearchContainer, mFadeDuration);
            mAppBarView.setState(AppBarView.State.EMPTY);
            return;
        }

        updateMetadata(mode);

        switch (mode) {
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
            case SEARCHING:
                ViewUtils.hideViewAnimated(mErrorContainer, mFadeDuration);
                ViewUtils.hideViewAnimated(mPlaybackContainer, mFadeDuration);
                ViewUtils.hideViewAnimated(mBrowseContainer, mFadeDuration);
                ViewUtils.showViewAnimated(mSearchContainer, mFadeDuration);
                mAppBarView.setState(AppBarView.State.SEARCHING);
                break;
        }
    }

    private void updateMetadata(Mode mode) {
        if (mode == Mode.PLAYBACK) {
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
        mAppBarView.openAppSelector();
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
        if (getInnerViewModel().useContentForwardBrowse()) {
            mPlaybackFragment.toggleQueueVisibility();
        } else {
            mDrawerController.showPlayQueue();
        }
    }

    public static class ViewModel extends AndroidViewModel {
        private LiveData<Bitmap> mAlbumArt;
        private MutableLiveData<Size> mAlbumArtSize = new MutableLiveData<>();
        private PlaybackViewModel mPlaybackViewModel;

        private boolean mContentForwardBrowseEnabled;
        private MutableLiveData<Boolean> mIsErrorState = new MutableLiveData<>();
        private MutableLiveData<Mode> mMode = new MutableLiveData<>();

        public ViewModel(@NonNull Application application) {
            super(application);
        }

        void init(@NonNull PlaybackViewModel playbackViewModel, boolean contentForwardBrowse) {
            if (mPlaybackViewModel == playbackViewModel) {
                return;
            }
            mPlaybackViewModel = playbackViewModel;
            mContentForwardBrowseEnabled = contentForwardBrowse;

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

        boolean useContentForwardBrowse() {
            return mContentForwardBrowseEnabled;
        }

        void setMode(Mode mode) {
            // If content forward is not enabled, then we always show the playback UI
            // (browse will be done in the drawer)
            mMode.setValue(mContentForwardBrowseEnabled ? mode : Mode.PLAYBACK);
        }

        LiveData<Pair<Mode, Boolean>> getModeAndErrorState() {
            return pair(mMode, mIsErrorState);
        }

        void setErrorState(boolean state) {
            mIsErrorState.setValue(state);
        }
    }
}
