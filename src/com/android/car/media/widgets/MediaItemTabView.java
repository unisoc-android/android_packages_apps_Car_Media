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

package com.android.car.media.widgets;

import android.content.Context;
import android.view.LayoutInflater;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.car.media.R;
import com.android.car.media.common.MediaItemMetadata;

/**
 * A view representing a media item to be included in the tab bar at the top of the UI.
 */
public class MediaItemTabView extends LinearLayout {
    private TextView mTitleView;
    private ImageView mImageView;

    /**
     * Creates a new tab for the given media item.
     */
    public MediaItemTabView(Context context, MediaItemMetadata item) {
        super(context);
        LayoutInflater inflater = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.tab_view, this, true);
        setOrientation(LinearLayout.VERTICAL);

        mImageView = findViewById(R.id.icon);
        MediaItemMetadata.updateImageView(context, item, mImageView, 0);
        mTitleView = findViewById(R.id.title);
        mTitleView.setText(item.getTitle());
    }
}
