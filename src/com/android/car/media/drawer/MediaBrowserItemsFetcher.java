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
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.session.MediaSessionCompat;

import androidx.car.drawer.DrawerItemViewHolder;

import com.android.car.media.MediaPlaybackModel;
import com.android.car.media.R;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link MediaItemsFetcher} implementation that fetches items from a specific
 * {@link MediaBrowserCompat}
 * node.
 * <p>
 * It optionally supports surfacing the Media app's queue as the last item.
 */
class MediaBrowserItemsFetcher implements MediaItemsFetcher {
    private static final String TAG = "Media.BrowserFetcher";

    /**
     * An id that can be returned from {@link MediaBrowserCompat.MediaItem#getMediaId()} to indicate
     * that a {@link android.media.browse.MediaBrowser.MediaItem} representing the play queue has
     * been clicked.
     */
    static final String PLAY_QUEUE_MEDIA_ID = "com.android.car.media.drawer.PLAY_QUEUE";

    private final Context mContext;
    private final MediaPlaybackModel mMediaPlaybackModel;
    private final String mMediaId;
    private final boolean mShowQueueItem;
    private final MediaItemOnClickListener mItemClickListener;
    private ItemsUpdatedCallback mCallback;
    private List<MediaBrowserCompat.MediaItem> mItems = new ArrayList<>();
    private boolean mQueueAvailable;

    MediaBrowserItemsFetcher(Context context, MediaPlaybackModel model,
            MediaItemOnClickListener listener, String mediaId, boolean showQueueItem) {
        mContext = context;
        mMediaPlaybackModel = model;
        mItemClickListener = listener;
        mMediaId = mediaId;
        mShowQueueItem = showQueueItem;
    }

    @Override
    public void start(ItemsUpdatedCallback callback) {
        mCallback = callback;
        updateQueueAvailability();
        if (mMediaPlaybackModel.isConnected()) {
            mMediaPlaybackModel.getMediaBrowser().subscribe(mMediaId, mSubscriptionCallback);
        } else {
            mItems.clear();
            callback.onItemsUpdated();
        }
        mMediaPlaybackModel.addListener(mModelListener);
    }

    private final MediaPlaybackModel.Listener mModelListener =
            new MediaPlaybackModel.AbstractListener() {
        @Override
        public void onQueueChanged(List<MediaSessionCompat.QueueItem> queue) {
            updateQueueAvailability();
        }
        @Override
        public void onSessionDestroyed(CharSequence destroyedMediaClientName) {
            updateQueueAvailability();
        }
        @Override
        public void onMediaConnectionSuspended() {
            if (mCallback != null) {
                mCallback.onItemsUpdated();
            }
        }
    };

    private final MediaBrowserCompat.SubscriptionCallback mSubscriptionCallback =
        new MediaBrowserCompat.SubscriptionCallback() {
            @Override
            public void onChildrenLoaded(String parentId,
                    List<MediaBrowserCompat.MediaItem> children) {
                mItems.clear();
                mItems.addAll(children);
                mCallback.onItemsUpdated();
            }

            @Override
            public void onError(String parentId) {
                mItems.clear();
                mCallback.onItemsUpdated();
            }
        };

    private void updateQueueAvailability() {
        if (mShowQueueItem && !mMediaPlaybackModel.getQueue().isEmpty()) {
            mQueueAvailable = true;
        }
    }

    @Override
    public int getItemCount() {
        int size = mItems.size();
        if (mQueueAvailable) {
            size++;
        }
        return size;
    }

    @Override
    public boolean usesSmallLayout(int position) {
        if (mQueueAvailable && position == mItems.size()) {
            return true;
        }
        return MediaItemsFetcher.usesSmallLayout(mItems.get(position).getDescription());
    }

    @Override
    public void populateViewHolder(DrawerItemViewHolder holder, int position) {
        if (mQueueAvailable && position == mItems.size()) {
            holder.getTitleView().setText(mMediaPlaybackModel.getQueueTitle());
            return;
        }
        MediaBrowserCompat.MediaItem item = mItems.get(position);
        MediaItemsFetcher.populateViewHolderFrom(holder, item.getDescription());

        if (holder.getEndIconView() == null) {
            return;
        }

        if (item.isBrowsable()) {
            int iconColor = mContext.getColor(R.color.car_tint);
            Drawable drawable = mContext.getDrawable(R.drawable.ic_chevron_right);
            drawable.setColorFilter(iconColor, PorterDuff.Mode.SRC_IN);
            holder.getEndIconView().setImageDrawable(drawable);
        } else {
            holder.getEndIconView().setImageDrawable(null);
        }
    }

    @Override
    public void onItemClick(int position) {
        if (mItemClickListener == null) {
            return;
        }

        MediaBrowserCompat.MediaItem item = mQueueAvailable && position == mItems.size()
                ? createPlayQueueMediaItem()
                : mItems.get(position);


        mItemClickListener.onMediaItemClicked(item);
    }

    /**
     * Creates and returns a {@link android.media.browse.MediaBrowser.MediaItem} that represents an
     * entry for the play queue. A play queue media item will have a media id of
     * {@link #PLAY_QUEUE_MEDIA_ID} and is {@link MediaBrowserCompat.MediaItem#FLAG_BROWSABLE}.
     */
    private MediaBrowserCompat.MediaItem createPlayQueueMediaItem() {
        MediaDescriptionCompat description = new MediaDescriptionCompat.Builder()
                .setMediaId(PLAY_QUEUE_MEDIA_ID)
                .setTitle(mMediaPlaybackModel.getQueueTitle())
                .build();

        return new MediaBrowserCompat.MediaItem(description,
                MediaBrowserCompat.MediaItem.FLAG_BROWSABLE);
    }

    @Override
    public void cleanup() {
        mMediaPlaybackModel.removeListener(mModelListener);
        mMediaPlaybackModel.getMediaBrowser().unsubscribe(mMediaId);
        mCallback = null;
    }

    @Override
    public int getScrollPosition() {
        return MediaItemsFetcher.DONT_SCROLL;
    }
}
