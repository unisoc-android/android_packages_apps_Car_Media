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
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.android.car.media.MetadataController;
import com.android.car.media.R;
import com.android.car.media.common.PlaybackControls;
import com.android.car.media.common.playback.PlaybackViewModel;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.lifecycle.LifecycleOwner;

public class BrowsePlaybackControlBar extends ConstraintLayout {

    private static final String TAG = "BrowsePlaybackControls";

    private MetadataController mMetadataController;
    private PlaybackControls mPlaybackControls;

    public BrowsePlaybackControlBar(Context context) {
        this(context, null);
    }

    public BrowsePlaybackControlBar(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BrowsePlaybackControlBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        LayoutInflater.from(context).inflate(R.layout.browse_playback_controls, this, true);
    }

    /**
     * Registers the {@link PlaybackViewModel} this widget will use to follow playback state.
     *
     * @param model {@link PlaybackViewModel} to watch.
     * @param owner the LifecycleOwner defining the scope of this View
     */
    public void setModel(@NonNull PlaybackViewModel model, @NonNull LifecycleOwner owner) {
        if (mMetadataController != null) {
            Log.w(TAG, "Model set more than once. Ignoring subsequent call.");
            return;
        }
        TextView title = findViewById(R.id.title);
        TextView subtitle = findViewById(R.id.subtitle);
        SeekBar seekBar = findViewById(R.id.seek_bar);
        ImageView albumArt = findViewById(R.id.album_art);
        mMetadataController = new MetadataController(owner, model,null,
                title, subtitle, null, seekBar, albumArt);

        mPlaybackControls = findViewById(R.id.playback_controls);
        mPlaybackControls.setModel(model, owner);
    }
}
