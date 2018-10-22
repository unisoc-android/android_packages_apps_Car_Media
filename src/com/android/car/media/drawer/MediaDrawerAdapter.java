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
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.car.drawer.CarDrawerController;
import androidx.car.drawer.DrawerItemViewHolder;
import androidx.lifecycle.LifecycleOwner;

import com.android.car.media.common.MediaItemMetadata;

import java.util.Collections;
import java.util.List;

/**
 * Base CarDrawerAdapter used by the Media app.
 */
abstract class MediaDrawerAdapter extends LifecycleDrawerAdapter {
    public static final int DONT_SCROLL = -1;
    private final CarDrawerController mDrawerController;
    final MediaItemOnClickListener mClickListener;
    @NonNull
    private List<MediaItemMetadata> mMediaItems = Collections.emptyList();
    private int mCurrentScrollPosition;

    MediaDrawerAdapter(Context context,
            LifecycleOwner parentLifecycle,
            CarDrawerController drawerController,
            @Nullable MediaItemOnClickListener clickListener) {
        super(context, parentLifecycle.getLifecycle(), true);
        mDrawerController = drawerController;
        mClickListener = clickListener;
    }

    final void setMediaItems(@Nullable List<MediaItemMetadata> mediaItems) {
        mMediaItems = mediaItems != null ? mediaItems : Collections.emptyList();
        notifyDataSetChanged();
    }

    @Override
    protected int getActualItemCount() {
        return mMediaItems.size();
    }

    protected MediaItemMetadata getItem(int position) {
        return mMediaItems.get(position);
    }

    @Override
    protected boolean usesSmallLayout(int position) {
        // Small layout is sufficient if there's no sub-title to display for the item.
        return TextUtils.isEmpty(getItem(position).getSubtitle());
    }

    @Override
    protected final void populateViewHolder(DrawerItemViewHolder holder, int position) {
        populateMainView(holder, position);
        if (holder.getEndIconView() != null) {
            populateEndIconView(holder.getEndIconView(), position);
        }
        scrollToCurrent();
        holder.itemView.setOnClickListener(v -> onItemClick(holder.getAdapterPosition()));
    }

    protected void populateMainView(DrawerItemViewHolder holder, int position) {
        MediaItemMetadata item = getItem(position);
        Context context = holder.itemView.getContext();
        holder.getTitleView().setText(item.getTitle());

        // If normal layout, populate subtitle.
        TextView bodyView = holder.getBodyView();
        if (bodyView != null) {
            bodyView.setText(item.getSubtitle());
        }

        ImageView iconView = holder.getIconView();
        MediaItemMetadata.updateImageView(context, item, iconView, 0);
        // TODO (robertoalexis): change updateImageView() to return boolean based on whether it
        // has something to display and use that in the if statement instead
        if (item.getAlbumArtBitmap() != null || item.getAlbumArtUri() != null) {
            iconView.setVisibility(View.VISIBLE);
        } else {
            iconView.setVisibility(View.GONE);
        }
    }

    protected abstract void populateEndIconView(ImageView endIconView, int position);

    protected abstract void onItemClick(int position);

    public void scrollToCurrent() {
        int scrollPosition = getScrollPosition();
        if (scrollPosition != DONT_SCROLL
                && mCurrentScrollPosition != scrollPosition) {
            mDrawerController.scrollToPosition(scrollPosition);
            mCurrentScrollPosition = scrollPosition;
        }
    }

    protected int getScrollPosition() {
        return DONT_SCROLL;
    }

}
