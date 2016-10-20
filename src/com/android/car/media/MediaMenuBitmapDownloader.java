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

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaDescription;
import android.net.Uri;
import android.os.Handler;
import android.support.car.app.menu.CarMenu;
import android.util.Log;
import com.android.car.apps.common.BitmapDownloader;
import com.android.car.apps.common.BitmapWorkerOptions;
import com.android.car.apps.common.UriUtils;

import java.lang.ref.WeakReference;

/**
 * Download the icon for car menu item. Once it is done, it will update the icon by calling
 * CarMenuCallbacks.notifyChildChanged(), which is needed to be called after CarMenu.sendResult().
 */
public class MediaMenuBitmapDownloader {
    private static final String TAG = "GH.MBDownloader";
    private static final int MAX_ALBUM_ART_DOWNLOAD_RETRIES = 10;
    private static final long ALBUM_ART_DOWNLOAD_RETRY_INTERVAL_MS = 1000;

    private final WeakReference<Context> mContext;
    private final MediaCarMenuCallbacks mCallback;
    private final String mParentId;
    private final String mChildId;
    private final Handler mHandler;
    private BitmapDownloadRunnable mBitmapDownloadRunnable;

    public MediaMenuBitmapDownloader(Context context, MediaCarMenuCallbacks callback,
            String parentId, String childId, Handler handler) {
        mContext = new WeakReference<>(context);
        mCallback = callback;
        mParentId = parentId;
        mChildId = childId;
        mHandler = handler;
    }

    public void setMenuBitmap(MediaDescription description) {
        if (description == null) {
            Log.w(TAG, "null media descriptor");
            return;
        }

        if (mBitmapDownloadRunnable != null) {
            mHandler.removeCallbacks(mBitmapDownloadRunnable);
            mBitmapDownloadRunnable.cancelDownload();
            mBitmapDownloadRunnable = null;
        }

        Bitmap bitmap = description.getIconBitmap();
        Uri iconUri = description.getIconUri();
        if (bitmap == null && iconUri == null) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "no bitmap or icon uri found");
            }
        } else if (bitmap != null) {
            updateIcon(bitmap);
        } else {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "downloading bitmap " + iconUri);
            }
            mBitmapDownloadRunnable = new BitmapDownloadRunnable(iconUri);
            mHandler.post(mBitmapDownloadRunnable);
        }
    }

    private void updateIcon(Bitmap bitmap) {
        mCallback.notifyChildChanged(mParentId,
                new CarMenu.Builder(mChildId).setIcon(bitmap).build());
    }

    private class BitmapDownloadRunnable implements Runnable {
        private Uri mIconUri;
        private int mRetries;
        private BitmapDownloader.BitmapCallback mBitmapCallback;

        public BitmapDownloadRunnable(Uri icon_uri) {
            mIconUri = icon_uri;
            mRetries = 0;
        }

        public void cancelDownload() {
            if (mBitmapCallback != null) {
                Context context = mContext.get();
                if (context == null) {
                    return;
                }

                BitmapDownloader.getInstance(context).cancelDownload(mBitmapCallback);
            }
        }

        @Override
        public void run() {
            mBitmapCallback = new BitmapDownloader.BitmapCallback() {
                @Override
                public void onBitmapRetrieved(Bitmap bitmap) {
                    if (bitmap == null) {
                        if (++mRetries <= MAX_ALBUM_ART_DOWNLOAD_RETRIES) {
                            if (Log.isLoggable(TAG, Log.DEBUG)) {
                                Log.d(TAG, "retrying after failing to download bitmap "
                                        + mIconUri.toString());
                            }
                            mHandler.postDelayed(BitmapDownloadRunnable.this,
                                    ALBUM_ART_DOWNLOAD_RETRY_INTERVAL_MS);
                        }
                    } else {
                        if (Log.isLoggable(TAG, Log.DEBUG)) {
                            Log.d(TAG, "downloaded bitmap " + mIconUri.toString() + " retries:"
                                    + mRetries);
                        }
                        updateIcon(bitmap);
                    }
                }
            };

            Context context = mContext.get();
            if (context == null) {
                return;
            }

            int bitmapSize =
                    context.getResources().getDimensionPixelSize(R.dimen.car_list_item_icon_size);
            BitmapDownloader.getInstance(context)
                    .getBitmap(
                            new BitmapWorkerOptions.Builder(context).resource(mIconUri)
                                    .height(bitmapSize)
                                    .width(bitmapSize)
                                    // We don't want to cache android resources as they are needed
                                    // to be refreshed after configuration changes.
                                    .cacheFlag(UriUtils.isAndroidResourceUri(mIconUri)
                                            ? (BitmapWorkerOptions.CACHE_FLAG_DISK_DISABLED
                                            | BitmapWorkerOptions.CACHE_FLAG_MEM_DISABLED)
                                            : 0)
                                    .build(),
                            mBitmapCallback);
        }
    }
}
