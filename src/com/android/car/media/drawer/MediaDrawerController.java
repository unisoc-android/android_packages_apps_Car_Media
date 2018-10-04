/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.car.media.drawer;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.car.drawer.CarDrawerAdapter;
import androidx.car.drawer.CarDrawerController;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelProviders;

import com.android.car.media.MediaActivity;
import com.android.car.media.common.MediaItemMetadata;
import com.android.car.media.common.browse.MediaBrowserViewModel;
import com.android.car.media.common.playback.PlaybackViewModel;
import com.android.car.media.common.source.MediaSourceViewModel;

/**
 * Manages drawer navigation and item selection.
 * <p>
 * Maintains separate MediaBrowserViewModels for each adapter, and each adapter serves as its own
 * Lifecycle (becomes inactive when not attached to a RecyclerView).
 */
public class MediaDrawerController implements MediaItemOnClickListener {
    private static final String TAG = "MediaDrawerController";

    private final Context mContext;
    private final LifecycleOwner mLifecycleOwner;
    private final CarDrawerController mDrawerController;
    private PlaybackViewModel.PlaybackController mPlaybackController;
    private final RootDrawerAdapter mRootAdapter;
    private final QueueDrawerAdapter mQueueAdapter;
    private final ViewModelProvider mViewModelProvider;

    public MediaDrawerController(MediaActivity activity, CarDrawerController drawerController) {
        mContext = activity;
        mLifecycleOwner = activity;
        mDrawerController = drawerController;
        mViewModelProvider = ViewModelProviders.of(activity);

        MediaSourceViewModel mediaSourceViewModel =
                mViewModelProvider.get(MediaSourceViewModel.class);
        PlaybackViewModel playbackViewModel = mViewModelProvider.get(PlaybackViewModel.class);

        mQueueAdapter = new QueueDrawerAdapter(mContext, mLifecycleOwner, playbackViewModel,
                mediaSourceViewModel, drawerController, this);

        MediaBrowserViewModel rootBrowser =
                MediaBrowserViewModel.Factory.getInstanceForBrowseRoot(mViewModelProvider);
        mRootAdapter = new RootDrawerAdapter(mContext, mLifecycleOwner, rootBrowser,
                mediaSourceViewModel, playbackViewModel, drawerController, this);

        playbackViewModel.getPlaybackController().observe(activity,
                controller -> mPlaybackController = controller);

    }

    @Override
    public void onQueueItemClicked(@NonNull MediaItemMetadata queueItem) {
        if (mPlaybackController != null) {
            mPlaybackController.skipToQueueItem(queueItem.getQueueId());
        }

        mDrawerController.closeDrawer();
    }

    @Override
    public void onMediaItemClicked(@NonNull MediaItemMetadata item) {
        if (RootDrawerAdapter.PLAY_QUEUE_MEDIA_ID.equals(item.getId())) {
            mDrawerController.pushAdapter(mQueueAdapter);
            return;
        }

        if (item.isBrowsable()) {
            BrowseDrawerAdapter browseDrawerAdapter = createChildAdapter(item.getId());
            mDrawerController.pushAdapter(browseDrawerAdapter);
        } else if (item.isPlayable()) {
            if (mPlaybackController != null) {
                mPlaybackController.playItem(item.getId());
            }
            mDrawerController.closeDrawer();
        } else {
            Log.w(TAG, "Unknown item type; don't know how to handle!");
        }
    }


    /**
     * Opens the drawer and displays the current playing queue of items. When the drawer is closed,
     * the view is switched back to the drawer root.
     */
    public void showPlayQueue() {
        mDrawerController.openDrawer();
        mDrawerController.pushAdapter(mQueueAdapter);
        mQueueAdapter.scrollToCurrent();
    }

    /**
     * @return Adapter to display root items of MediaBrowse tree. {@link #showPlayQueue()} can be
     * used to display items from the queue.
     */
    public CarDrawerAdapter getRootAdapter() {
        return mRootAdapter;
    }

    private BrowseDrawerAdapter createChildAdapter(String browseId) {
        MediaBrowserViewModel browserViewModel =
                MediaBrowserViewModel.Factory.getInstanceForBrowseId(mViewModelProvider, browseId);
        return new BrowseDrawerAdapter(mContext, mLifecycleOwner, browserViewModel,
                mDrawerController, this);

    }
}
