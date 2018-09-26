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
import com.android.car.media.common.MediaItemMetadata;
import com.android.car.media.common.browse.MediaBrowserViewModel;

/**
 * CarDrawerAdapter that shows the media items in the browse tree. Displays the data from a {@link
 * MediaBrowserViewModel}.
 */
public class BrowseDrawerAdapter extends MediaDrawerAdapter {
    BrowseDrawerAdapter(Context context,
            LifecycleOwner parentLifecycle,
            MediaBrowserViewModel mediaBrowserViewModel,
            CarDrawerController drawerController,
            @Nullable MediaItemOnClickListener clickListener) {
        super(context, parentLifecycle, drawerController, clickListener);
        mediaBrowserViewModel.isLoading().observe(this, drawerController::showLoadingProgressBar);
        mediaBrowserViewModel.getBrowsedMediaItems().observe(this, this::setMediaItems);
    }

    protected void onItemClick(int position) {
        if (mClickListener != null) {
            mClickListener.onMediaItemClicked(getItem(position));
        }
    }

    @Override
    protected void populateEndIconView(ImageView endIconView, int position) {
        MediaItemMetadata item = getItem(position);
        Context context = endIconView.getContext();
        if (item.isBrowsable()) {
            int iconColor = context.getColor(R.color.car_tint);
            Drawable drawable = context.getDrawable(R.drawable.ic_chevron_right);
            drawable.setColorFilter(iconColor, PorterDuff.Mode.SRC_IN);
            endIconView.setImageDrawable(drawable);
        } else {
            endIconView.setImageDrawable(null);
        }
    }
}
