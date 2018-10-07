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

package com.android.car.media.drawer;

import android.content.Context;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;

import androidx.annotation.Nullable;
import androidx.car.drawer.CarDrawerController;
import androidx.car.drawer.DrawerItemViewHolder;
import androidx.lifecycle.LifecycleOwner;

import com.android.car.media.common.MediaItemMetadata;
import com.android.car.media.common.browse.MediaBrowserViewModel;
import com.android.car.media.common.playback.PlaybackViewModel;
import com.android.car.media.common.source.MediaSourceViewModel;

/**
 * Extension of {@link BrowseDrawerAdapter} that allows showing a "Queue" item. Its {@link
 * MediaBrowserViewModel} will be set to browse the root of the tree.
 */
public class RootDrawerAdapter extends BrowseDrawerAdapter {
    /**
     * An id that can be returned from {@link MediaBrowserCompat.MediaItem#getMediaId()} to indicate
     * that a {@link android.media.browse.MediaBrowser.MediaItem} representing the play queue has
     * been clicked.
     */
    static final String PLAY_QUEUE_MEDIA_ID = "com.android.car.media.drawer.PLAY_QUEUE";

    private boolean mQueueAvailable;
    private CharSequence mQueueTitle;

    RootDrawerAdapter(Context context, LifecycleOwner parentLifecycle,
            MediaBrowserViewModel rootBrowserViewModel,
            MediaSourceViewModel mediaSourceViewModel,
            PlaybackViewModel playbackViewModel,
            CarDrawerController drawerController,
            MediaItemOnClickListener clickListener) {
        super(context, parentLifecycle, rootBrowserViewModel, drawerController, clickListener);
        playbackViewModel.hasQueue().observe(this, this::setQueueAvailable);
        playbackViewModel.getQueueTitle().observe(this, this::setQueueTitle);
        mediaSourceViewModel.getSelectedMediaSource().observe(this,
                mediaSource -> setTitle(mediaSource == null ? "" : mediaSource.getName()));
    }

    private void setQueueAvailable(boolean queueAvailable) {
        mQueueAvailable = queueAvailable;
        notifyDataSetChanged();
    }

    private void setQueueTitle(@Nullable CharSequence queueTitle) {
        mQueueTitle = queueTitle;
    }

    @Override
    protected int getActualItemCount() {
        int superCount = super.getActualItemCount();
        if (mQueueAvailable) {
            return superCount + 1;
        } else {
            return superCount;
        }
    }

    @Override
    protected MediaItemMetadata getItem(int position) {
        if (isQueuePosition(position)) {
            return createPlayQueueMediaItem();
        }
        return super.getItem(position);
    }

    @Override
    protected boolean usesSmallLayout(int position) {
        if (isQueuePosition(position)) {
            return true;
        }
        return super.usesSmallLayout(position);
    }

    @Override
    protected void populateMainView(DrawerItemViewHolder holder, int position) {
        if (isQueuePosition(position)) {
            holder.getTitleView().setText(mQueueTitle);
            return;
        }
        super.populateMainView(holder, position);
    }

    private boolean isQueuePosition(int position) {
        return mQueueAvailable && position == super.getActualItemCount();
    }


    /**
     * Creates and returns a {@link android.media.browse.MediaBrowser.MediaItem} that represents an
     * entry for the play queue. A play queue media item will have a media id of {@link
     * #PLAY_QUEUE_MEDIA_ID} and is {@link MediaBrowserCompat.MediaItem#FLAG_BROWSABLE}.
     */
    private MediaItemMetadata createPlayQueueMediaItem() {
        MediaDescriptionCompat description = new MediaDescriptionCompat.Builder()
                .setMediaId(PLAY_QUEUE_MEDIA_ID)
                .setTitle(mQueueTitle)
                .build();

        return new MediaItemMetadata(
                new MediaBrowserCompat.MediaItem(description,
                        MediaBrowserCompat.MediaItem.FLAG_BROWSABLE));
    }
}
