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

package com.android.car.media;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.android.car.media.common.MediaItemMetadata;
import com.android.car.media.common.MediaSource;
import com.android.car.media.common.PlaybackControls;
import com.android.car.media.common.PlaybackModel;
import com.android.car.media.widgets.MetadataView;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import androidx.car.widget.ListItem;
import androidx.car.widget.ListItemAdapter;
import androidx.car.widget.ListItemProvider;
import androidx.car.widget.PagedListView;
import androidx.car.widget.TextListItem;

/**
 * A {@link Fragment} that implements both the playback and the content forward browsing experience.
 * It observes a {@link PlaybackModel} and updates its information depending on the currently
 * playing media source through the {@link android.media.session.MediaSession} API.
 */
public class PlaybackFragment extends Fragment {
    private static final String TAG = "PlaybackFragment";
    private static final DateFormat TIME_FORMAT = new SimpleDateFormat("m:ss", Locale.US);

    private PlaybackModel mModel;
    private PlaybackControls mPlaybackControls;
    private ImageView mAlbumArt;
    private MetadataView mMetadataView;
    private PagedListView mQueueList;
    private QueueItemsAdapter mQueueAdapter;
    private MediaItemMetadata mCurrentMetadata;
    private PlaybackModel.PlaybackObserver mPlaybackObserver = new PlaybackModel.PlaybackObserver() {
        @Override
        public void onPlaybackStateChanged() {
            updateState();
        }

        @Override
        public void onSourceChanged() {
            updateState();
            updateMetadata();
            updateAccentColor();
        }

        @Override
        public void onMetadataChanged() {
            updateMetadata();
        }
    };
    private ListItemProvider mQueueItemsProvider = new ListItemProvider() {
        @Override
        public ListItem get(int position) {
            if (!mModel.hasQueue()) {
                return null;
            }
            List<MediaItemMetadata> queue = mModel.getQueue();
            if (position < 0 || position >= queue.size()) {
                return null;
            }
            MediaItemMetadata item = queue.get(position);
            TextListItem textListItem = new TextListItem(getContext());
            textListItem.setTitle(item.getTitle().toString());
            textListItem.setBody(item.getSubtitle().toString());
            textListItem.setOnClickListener(v -> onQueueItemClicked(item));
            return textListItem;
        }
        @Override
        public int size() {
            if (!mModel.hasQueue()) {
                return 0;
            }
            return mModel.getQueue().size();
        }
    };
    private static class QueueItemsAdapter extends ListItemAdapter {
        QueueItemsAdapter(Context context, ListItemProvider itemProvider) {
            super(context, itemProvider, BackgroundStyle.SOLID);
        }

        void refresh() {
            // TODO: Perform a diff between current and new content and trigger the proper
            // RecyclerView updates.
            this.notifyDataSetChanged();
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, final ViewGroup container,
            Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_playback, container, false);
        mModel = new PlaybackModel(getContext());
        mPlaybackControls = view.findViewById(R.id.playback_controls);
        mPlaybackControls.setModel(mModel);
        ViewGroup playbackContainer = view.findViewById(R.id.playback_container);
        mPlaybackControls.setAnimationViewGroup(playbackContainer);
        mAlbumArt = view.findViewById(R.id.album_art);
        mMetadataView = view.findViewById(R.id.metadata);
        mQueueList = view.findViewById(R.id.queue_list);
        RecyclerView recyclerView = mQueueList.getRecyclerView();
        recyclerView.setVerticalFadingEdgeEnabled(true);
        recyclerView.setFadingEdgeLength(getResources()
                .getDimensionPixelSize(R.dimen.car_padding_4));
        mQueueAdapter = new QueueItemsAdapter(getContext(), mQueueItemsProvider);
        mQueueList.setAdapter(mQueueAdapter);

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        mModel.registerObserver(mPlaybackObserver);
        mMetadataView.setModel(mModel);
    }

    @Override
    public void onStop() {
        super.onStop();
        mModel.unregisterObserver(mPlaybackObserver);
        mMetadataView.setModel(null);
        mCurrentMetadata = null;
    }

    private void updateState() {
        mQueueAdapter.refresh();
    }

    private void updateMetadata() {
        MediaItemMetadata metadata = mModel.getMetadata();
        if (Objects.equals(mCurrentMetadata, metadata)) {
            return;
        }
        mCurrentMetadata = metadata;
        MediaItemMetadata.updateImageView(getContext(), metadata, mAlbumArt, 0);
    }

    private void updateAccentColor() {
        int defaultColor = getResources().getColor(android.R.color.background_dark, null);
        MediaSource mediaSource = mModel.getMediaSource();
        int color = mediaSource == null ? defaultColor : mediaSource.getAccentColor(defaultColor);
        // TODO: Update queue color
    }

    private void onQueueItemClicked(MediaItemMetadata item) {
        mModel.onSkipToQueueItem(item.getQueueId());
    }

    /**
     * Collapses the playback controls.
     */
    public void closeOverflowMenu() {
        mPlaybackControls.close();
    }
}
