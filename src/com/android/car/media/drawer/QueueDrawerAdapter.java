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
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.car.drawer.CarDrawerController;
import androidx.lifecycle.LifecycleOwner;

import com.android.car.media.R;
import com.android.car.media.common.playback.PlaybackViewModel;
import com.android.car.media.common.source.MediaSourceColors;
import com.android.car.media.common.source.MediaSourceViewModel;

/**
 * CarDrawerAdapter that displays the current queue.
 */
public class QueueDrawerAdapter extends MediaDrawerAdapter {
    private Integer mActiveQueuePosition;
    private int mPrimaryColor;

    QueueDrawerAdapter(Context context, LifecycleOwner parentLifecycle,
            PlaybackViewModel playbackViewModel,
            MediaSourceViewModel mediaSourceViewModel,
            CarDrawerController drawerController,
            MediaItemOnClickListener clickListener) {
        super(context, parentLifecycle, drawerController, clickListener);
        MediaSourceColors.Factory colorsFactory = new MediaSourceColors.Factory(context);
        playbackViewModel.getPlaybackInfo().getActiveQueuePosition()
                .observe(this, this::setActiveQueuePosition);
        playbackViewModel.getQueue().observe(this, this::setMediaItems);
        mediaSourceViewModel.getPrimaryMediaSource()
                .observe(this, mediaSource ->
                        this.setPrimaryColor(
                                colorsFactory.extractColors(mediaSource).getPrimaryColor(0)));
    }

    private void setActiveQueuePosition(@Nullable Integer position) {
        mActiveQueuePosition = position;
        notifyDataSetChanged();
    }

    private void setPrimaryColor(int primaryColor) {
        mPrimaryColor = primaryColor;
    }

    @Override
    protected int getScrollPosition() {
        if (mActiveQueuePosition == null) {
            return DONT_SCROLL;
        }
        return mActiveQueuePosition;
    }

    @Override
    protected void onItemClick(int position) {
        if (mClickListener != null) {
            mClickListener.onQueueItemClicked(getItem(position));
        }
    }

    @Override
    protected void populateEndIconView(ImageView endIconView, int position) {
        Context context = endIconView.getContext();
        if (mActiveQueuePosition != null && (position == mActiveQueuePosition)) {
            Drawable drawable = context.getDrawable(R.drawable.ic_music_active);
            drawable.setColorFilter(mPrimaryColor, PorterDuff.Mode.SRC_IN);
            endIconView.setImageDrawable(drawable);
        } else {
            endIconView.setImageBitmap(null);
        }
    }
}
