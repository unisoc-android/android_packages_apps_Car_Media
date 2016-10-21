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
import android.content.Context;
import android.content.res.Resources;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.media.browse.MediaBrowser;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Handler;
import android.support.car.app.menu.CarMenu;
import android.support.car.app.menu.CarMenuCallbacks;
import android.support.car.app.menu.RootMenu;
import android.support.car.app.menu.compat.CarMenuConstantsComapt;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages all data needed for media drawer menu.
 */
public class MediaCarMenuCallbacks extends CarMenuCallbacks {

    public static final String QUEUE_ROOT = "QUEUE_ROOT";

    private static final String TAG = "GH.MediaMenuCallbacks";
    // MEDIA_APP_ROOT is used for onGetRoot() of MediaMenuCallbacks, which is called so early that
    // MediaBrowser hasn't got the root already. So we return this default root first and store the
    // real one in mRootId.
    private static final String MEDIA_APP_ROOT = "MEDIA_APP_ROOT";
    private static final String EXTRA_ICON_SIZE =
            "com.google.android.gms.car.media.BrowserIconSize";
    private static final String QUEUE_ITEM_PREFIX = "queue_item_prefix_";
    private static final String MEDIA_QUEUE_EMPTY_PLACEHOLDER = "media_queue_emtpy_placeholder";

    private final MediaActivity mActivity;
    private final Context mContext;
    private final Handler mHandler;
    private MediaBrowser mBrowser;
    private MediaController mController;
    private CarMenu mMenuResult;
    private String mMediaId;
    private String mRootId;
    // The media id we want to subscribe but media browser is not connected at that time.
    private String mPendingMediaId;
    private long mActiveQueueItemId;
    private boolean mLoadQueueMenuPending;
    // Whether we add "Queue" as the last item in the main menu.
    private boolean mIsQueueInMenu;
    private List<MediaBrowser.MediaItem> mItems;
    private LoadQueueBitmapRunnable mLoadQueueBitmapRunnable;
    private LoadMenuBitmapRunnable mLoadMenuBitmapRunnable;
    // The parent ID is set whenever there's a onChildrenLoaded request.
    private UpdateMenuRunnable mUpdateMenuRunnable = new UpdateMenuRunnable();

    public MediaCarMenuCallbacks(MediaActivity activity) {
        mActivity = activity;
        mContext = activity.getContext();
        mHandler = new Handler();
        MediaManager.getInstance(mContext).addListener(mListener);
    }

    public void cleanup() {
        MediaManager.getInstance(mContext).removeListener(mListener);
        mHandler.removeCallbacksAndMessages(null);
        if (mBrowser != null) {
            if (mMediaId != null) {
                mBrowser.unsubscribe(mMediaId);
                mMediaId = null;
            }
            mBrowser.disconnect();
            mBrowser = null;
        }
        if (mController != null) {
            mController.unregisterCallback(mControllerCallback);
            mController = null;
        }
    }

    @Override
    public RootMenu onGetRoot(Bundle hints) {
        // Return the default fake root due to the real one maybe not ready at this time.
        return new RootMenu(MEDIA_APP_ROOT);
    }

    @Override
    public void onLoadChildren(String parentId, CarMenu result) {
        Log.d(TAG, "onLoadChildren " + parentId);
        resetCarMenu(result);
        if (QUEUE_ROOT.equals(parentId)) {
            // If mBrowser is not connected now, we will load the menu later when it is connected.
            if (mBrowser == null || !mBrowser.isConnected()) {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "MediaBrowser is not connected while loading menu.");
                }
                mLoadQueueMenuPending = true;
                return;
            }

            // Unsubscribe the old id first, or else it will affect to subscribe the new one.
            if (!TextUtils.isEmpty(mMediaId) && !QUEUE_ROOT.equals(mMediaId)) {
                mBrowser.unsubscribe(mMediaId);
            }
            mMediaId = parentId;

            loadQueueMenu();
        } else {
            // If mBrowser is not connected now, we will load the menu later when it is connected.
            if (mBrowser == null || !mBrowser.isConnected()) {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "MediaBrowser is not connected while loading menu.");
                }
                mPendingMediaId = parentId;
                return;
            }

            // Unsubscribe the old id first, or else it will affect to subscribe the new one.
            if (!TextUtils.isEmpty(mMediaId) && !QUEUE_ROOT.equals(mMediaId)) {
                mBrowser.unsubscribe(mMediaId);
            }
            // Replace the fake root id with the real one, then we can use it to subscribe.
            if (parentId.equals(MEDIA_APP_ROOT)) {
                mMediaId = mRootId;
            } else {
                mMediaId = parentId;
            }
            mBrowser.subscribe(mMediaId, mSubscriptionCallback);
        }
    }

    @Override
    public void onItemClicked(String id) {
        // We treat queue item specially because its id is different from the normal one.
        if (id.startsWith(QUEUE_ITEM_PREFIX)) {
            String index = id.substring(QUEUE_ITEM_PREFIX.length());
            mController.getTransportControls().skipToQueueItem(Long.valueOf(index));
            mActivity.closeDrawer();
        } else {
            if (mItems == null) {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Media menu is empty.");
                }
                return;
            }

            for (MediaBrowser.MediaItem item : mItems) {
                if (item.getMediaId().equals(id)) {
                    if (item.isPlayable()) {
                        if (mController != null) {
                            mController.getTransportControls().pause();
                            mController.getTransportControls().playFromMediaId(item.getMediaId(),
                                    item.getDescription().getExtras());
                        } else {
                            Log.e(TAG, "MediaSession is destroyed.");
                        }
                        mActivity.closeDrawer();
                    }
                    break;
                }
            }
        }
    }

    private void resetCarMenu(CarMenu result) {
        // Stop loading previous menu due to we are under the new one now.
        if (mMenuResult != null) {
            if (mUpdateMenuRunnable != null) {
                mHandler.removeCallbacks(mUpdateMenuRunnable);
                // Spot fix. This runnable is being used in the subscription callbacks and is
                // causing a crash. The lifecycle here is a little messed up and needs to be
                // straightened out but for now just set it to a new object instead of setting
                // it to null.
                mUpdateMenuRunnable = new UpdateMenuRunnable();
            }
            if (mLoadMenuBitmapRunnable != null) {
                mHandler.removeCallbacks(mLoadMenuBitmapRunnable);
                mLoadMenuBitmapRunnable = null;
            }
            if (mLoadQueueBitmapRunnable != null) {
                mHandler.removeCallbacks(mLoadQueueBitmapRunnable);
                mLoadQueueBitmapRunnable = null;
            }
        }
        mMenuResult = result;
        mMenuResult.detach();
    }

    private CarMenu.Item emptyQueueMenu() {
        CarMenu.Builder builder = new CarMenu.Builder(MEDIA_QUEUE_EMPTY_PLACEHOLDER);

        final int iconColor = mContext.getResources().getColor(R.color.car_tint);
        Drawable drawable = mContext.getResources().getDrawable(R.drawable.ic_list_view_disable);
        drawable.setColorFilter(iconColor, PorterDuff.Mode.SRC_IN);
        builder.setIconFromSnapshot(drawable);
        builder.setIsEmptyPlaceHolder(true);
        return builder.build();
    }

    private void loadQueueMenu() {
        if (mMenuResult == null) {
            Log.w(TAG, "CarMenu is null while loading queue menu.");
            return;
        }

        List<CarMenu.Item> menuItems = new ArrayList<>();
        if (mController == null) {
            Log.w(TAG, "MediaController is null while loading queue menu.");

            // Add a icon for empty menu.
            sendEmptyMenu();
        } else {
            List<MediaSession.QueueItem> queue = mController.getQueue();
            mActiveQueueItemId = getActiveQueueItemId();
            boolean hasImages = false;
            for (MediaSession.QueueItem item : queue) {
                if ((item.getDescription().getIconUri() != null)
                        || (item.getDescription().getIconBitmap() != null)) {
                    hasImages = true;
                    break;
                }
            }
            boolean activeQueueItemFound = false;
            for (final MediaSession.QueueItem item : queue) {
                // Only queue items following the active item are displayed in the menu.
                if (item.getQueueId() == mActiveQueueItemId) {
                    activeQueueItemFound = true;
                }

                if (activeQueueItemFound) {
                    CarMenu.Builder builder =
                            new CarMenu.Builder(QUEUE_ITEM_PREFIX + item.getQueueId());
                    builder.setTitle(item.getDescription().getTitle().toString())
                            .setText(item.getDescription().getSubtitle().toString());
                    // Place empty bitmap as place holder first, we will load the bitamp later.
                    if (hasImages) {
                        builder.setIcon(null);
                    }
                    if (item.getQueueId() == mActiveQueueItemId) {
                        int primaryColor =
                                MediaManager.getInstance(mContext).getMediaClientPrimaryColor();
                        Drawable drawable =
                                mContext.getResources().getDrawable(R.drawable.ic_music_active);
                        drawable.setColorFilter(primaryColor, PorterDuff.Mode.SRC_IN);
                        builder.setRightIconFromSnapshot(drawable);
                    }
                    menuItems.add(builder.build());
                }
            }

            // If we have not found any items then set the menu to empty placeholder item.
            if (menuItems.size() == 0) {
                sendEmptyMenu();
            } else {
                mMenuResult.sendResult(menuItems);
                mMenuResult = null;
            }

            if (hasImages) {
                if (mLoadQueueBitmapRunnable != null) {
                    mHandler.removeCallbacks(mLoadQueueBitmapRunnable);
                }
                mLoadQueueBitmapRunnable = new LoadQueueBitmapRunnable(queue, QUEUE_ROOT);
                mHandler.post(mLoadQueueBitmapRunnable);
            }
        }
    }

    private void sendEmptyMenu() {
        if (mMenuResult != null) {
            List<CarMenu.Item> menuItems = new ArrayList<CarMenu.Item>();
            menuItems.add(emptyQueueMenu());
            mMenuResult.sendResult(menuItems);
            mMenuResult = null;
        }
    }

    private boolean enableQueueItem(List<MediaSession.QueueItem> items) {
        if (items == null || mController == null) {
            return false;
        }

        if (mIsQueueInMenu) {
            // We already have a queue item; do nothing
            return false;
        }
        if (TextUtils.isEmpty(mController.getQueueTitle())) {
            // No queue title to show; do nothing
            return false;
        }
        return true;
    }

    private long getActiveQueueItemId() {
        if (mController == null) {
            return MediaSession.QueueItem.UNKNOWN_ID;
        }

        PlaybackState playbackState = mController.getPlaybackState();
        if (playbackState != null) {
            return playbackState.getActiveQueueItemId();
        } else {
            return MediaSession.QueueItem.UNKNOWN_ID;
        }
    }

    private final MediaManager.Listener mListener = new MediaManager.Listener() {

        @Override
        public void onMediaAppChanged(ComponentName componentName) {
            mRootId = null;
            if (mBrowser != null) {
                // Unsubscribe the old id first, or else it will affect to subscribe the new one.
                if (!TextUtils.isEmpty(mMediaId) && !QUEUE_ROOT.equals(mMediaId)) {
                    mBrowser.unsubscribe(mMediaId);
                    mMediaId = null;
                }
                mBrowser.disconnect();
                mBrowser = null;
            }
            Resources resources = mContext.getResources();
            Bundle extras = new Bundle();
            if (resources != null) {
                extras.putInt(EXTRA_ICON_SIZE,
                        resources.getDimensionPixelSize(R.dimen.car_list_item_icon_size));
            }
            mBrowser = new MediaBrowser(mContext, componentName, mConnectionCallbacks, extras);
            if (mController != null) {
                mController.unregisterCallback(mControllerCallback);
                mController = null;
            }
            mBrowser.connect();
            // Only store MediaManager instance to a local variable when it is short lived.
            MediaManager mediaManager = MediaManager.getInstance(mContext);
            mActivity.setTitle(mediaManager.getMediaClientName().toString());
            mActivity.setScrimColor(mediaManager.getMediaClientPrimaryColorDark());
            mActivity.attachContentFragment();
        }

        @Override
        public void onStatusMessageChanged(String msg) {}
    };

    private final MediaBrowser.ConnectionCallback mConnectionCallbacks =
            new MediaBrowser.ConnectionCallback() {

        @Override
        public void onConnected() {
            // Get the real root and will replace it with the default fake one which is set
            // in onGetRoot().
            mRootId = mBrowser.getRoot();
            if (mPendingMediaId != null) {
                mMediaId = mPendingMediaId.equals(MEDIA_APP_ROOT) ? mRootId : mPendingMediaId;
                mPendingMediaId = null;
            } else {
                mMediaId = mRootId;
            }
            MediaSession.Token token = mBrowser.getSessionToken();
            if (token != null) {
                mController = new MediaController(mContext, token);
                mController.registerCallback(mControllerCallback);
            } else {
                // We will still be able to browse media content, but not able to play them.
                Log.e(TAG, "Media session token is null for "
                        + MediaManager.getInstance(mContext).getMediaClientName());
            }
            if (mLoadQueueMenuPending) {
                mLoadQueueMenuPending = false;
                loadQueueMenu();
            } else {
                mBrowser.subscribe(mMediaId, mSubscriptionCallback);
            }
        }

        @Override
        public void onConnectionSuspended() {
            Log.w(TAG, "Media browser service connection suspended. Waiting to be"
                    + " reconnected....");
        }

        @Override
        public void onConnectionFailed() {
            Log.e(TAG, "Media browser service connection FAILED!");
            sendEmptyMenu();
            // disconnect anyway to make sure we get into a sanity state
            mBrowser.disconnect();
            mBrowser = null;
        }
    };

    private final MediaController.Callback mControllerCallback = new MediaController.Callback() {

        @Override
        public void onSessionDestroyed() {
            Log.e(TAG, "Media session is destroyed");
            sendEmptyMenu();
            if (mController != null) {
                mController.unregisterCallback(mControllerCallback);
            }
            mController = null;
        }

        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            long activeQueueItemId = getActiveQueueItemId();
            if (mActiveQueueItemId != activeQueueItemId) {
                if (mMediaId == QUEUE_ROOT) {
                    // After this call, the whole queue menu will be refreshed.
                    notifyChildrenChanged(QUEUE_ROOT);
                }
                mActiveQueueItemId = activeQueueItemId;
            }
        }

        @Override
        public void onQueueChanged(List<MediaSession.QueueItem> queue) {
            if (mMediaId == mRootId && enableQueueItem(queue)) {
                notifyChildrenChanged(MEDIA_APP_ROOT);
            }
        }
    };

    private final MediaBrowser.SubscriptionCallback mSubscriptionCallback =
            new MediaBrowser.SubscriptionCallback() {

        @Override
        public void onChildrenLoaded(String parentId, List<MediaBrowser.MediaItem> children) {
            Log.d(TAG, "onChildrenLoaded" + parentId);
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Loaded " + children.size() + " children.");
                for (MediaBrowser.MediaItem item : children) {
                    Log.d(TAG, "\t" + item.getDescription().getTitle());
                }
            }

            mIsQueueInMenu = false;
            if (mController == null) {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "MediaController is null in SubscriptionCallback.");
                }
                sendEmptyMenu();
                // the session has been destroyed or we have moved to another facet.
                return;
            }

            mItems = new ArrayList<>(children);
            mHandler.removeCallbacks(mUpdateMenuRunnable);
            mUpdateMenuRunnable.setParentId(parentId);
            mHandler.post(mUpdateMenuRunnable);
        }

        @Override
        public void onError(String mediaId) {
            Log.e(TAG, "onError getting items for " + mediaId);
            sendEmptyMenu();
        }
    };

    private class UpdateMenuRunnable implements Runnable {
        private String mParentId;

        void setParentId(String parentId) {
            mParentId = parentId;
        }

        @Override
        public void run() {
            if (mMenuResult == null) {
                Log.e(TAG, "CarMenu is null while update menu, notify change instead.");
                notifyChildrenChanged(mParentId);
                return;
            }

            if (mItems == null) {
                throw new IllegalArgumentException(
                        "You must supply CarMenu with a list of MediaItems.");
            }

            boolean hasImages = false;
            for (MediaBrowser.MediaItem item : mItems) {
                if ((item.getDescription().getIconUri() != null)
                        || (item.getDescription().getIconBitmap() != null)) {
                    hasImages = true;
                    break;
                }
            }
            List<CarMenu.Item> menuItems = new ArrayList<>();
            for (MediaBrowser.MediaItem item : mItems) {
                menuItems.add(convertMediaItemToMenuItem(item, hasImages));
            }
            // If it is under root menu and play queue is not empty, add "Queue" item to the menu.
            if (mMediaId.equals(mRootId) && mController != null) {
                List<MediaSession.QueueItem> queue = mController.getQueue();
                if (queue != null && queue.size() > 0
                        && !TextUtils.isEmpty(mController.getQueueTitle())) {
                    String queueTitle = mController.getQueueTitle().toString();
                    menuItems.add(new CarMenu.Builder(QUEUE_ROOT).setTitle(queueTitle)
                            .setFlags(CarMenuConstantsComapt.MenuItemConstants.FLAG_BROWSABLE)
                            .build());
                    mIsQueueInMenu = true;
                }
            }
            if (menuItems.size() == 0) {
                sendEmptyMenu();
            } else {
                mMenuResult.sendResult(menuItems);
                mMenuResult = null;
            }

            if (hasImages) {
                if (mLoadMenuBitmapRunnable != null) {
                    mHandler.removeCallbacks(mLoadMenuBitmapRunnable);
                }
                // Due to we return fake root id in onGetRoot(), when we call notifyChildChanged()
                // we still need to use the fake root id instead of the real one.
                if (mMediaId.equals(mRootId)) {
                    mLoadMenuBitmapRunnable = new LoadMenuBitmapRunnable(mItems, MEDIA_APP_ROOT);
                } else {
                    mLoadMenuBitmapRunnable = new LoadMenuBitmapRunnable(mItems, mMediaId);
                }
                mHandler.post(mLoadMenuBitmapRunnable);
            }
        }

        /**
         * Returns CarMenu.Item which is used in rendering menu.
         *
         * @param item MediaItem which has all info to render menu.
         * @param hasImages Whether the menu item has image or not.
         * @return menu item.
         */
        private CarMenu.Item convertMediaItemToMenuItem(MediaBrowser.MediaItem item,
                boolean hasImages) {
            CarMenu.Builder builder = new CarMenu.Builder(item.getMediaId());
            CharSequence title = item.getDescription().getTitle();
            if (title != null) {
                builder.setTitle(title.toString());
            }
            CharSequence subTitle = item.getDescription().getSubtitle();
            if (subTitle != null) {
                builder.setText(subTitle.toString());
            }
            if (item.isBrowsable()) {
                builder.setFlags(CarMenuConstantsComapt.MenuItemConstants.FLAG_BROWSABLE);
            }
            // Place empty bitmap as place holder first, we will load the bitamp later.
            if (hasImages) {
                builder.setIcon(null);
            }
            return builder.build();
        }
    }

    private class LoadQueueBitmapRunnable implements Runnable {
        private final List<MediaSession.QueueItem> mQueue;
        private final String mParentId;

        public LoadQueueBitmapRunnable(List<MediaSession.QueueItem> queue, String parentId) {
            mQueue = queue;
            mParentId = parentId;
        }

        @Override
        public void run() {
            boolean activeQueueItemFound = false;
            for (MediaSession.QueueItem item : mQueue) {
                if (item.getQueueId() == mActiveQueueItemId) {
                    activeQueueItemFound = true;
                }

                if (activeQueueItemFound) {
                    MediaMenuBitmapDownloader downloader = new MediaMenuBitmapDownloader(mContext,
                            MediaCarMenuCallbacks.this, mParentId,
                            QUEUE_ITEM_PREFIX + item.getQueueId(), mHandler);
                    downloader.setMenuBitmap(item.getDescription());
                }
            }
        }
    }

    private class LoadMenuBitmapRunnable implements Runnable {
        private List<MediaBrowser.MediaItem> mItemList;
        private String mParentId;

        public LoadMenuBitmapRunnable(List<MediaBrowser.MediaItem> itemList, String parentId) {
            mItemList = itemList;
            mParentId = parentId;
        }

        @Override
        public void run() {
            for (MediaBrowser.MediaItem item : mItemList) {
                MediaMenuBitmapDownloader downloader = new MediaMenuBitmapDownloader(mContext,
                        MediaCarMenuCallbacks.this, mParentId, item.getMediaId(), mHandler);
                downloader.setMenuBitmap(item.getDescription());
            }
        }
    }
}
