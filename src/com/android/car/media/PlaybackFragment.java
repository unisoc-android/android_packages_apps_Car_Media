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
import android.support.annotation.NonNull;
import android.support.constraint.ConstraintLayout;
import android.support.constraint.ConstraintSet;
import android.support.v4.app.Fragment;
import android.support.v7.widget.RecyclerView;
import android.transition.Transition;
import android.transition.TransitionInflater;
import android.transition.TransitionManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.car.widget.ListItem;
import androidx.car.widget.ListItemAdapter;
import androidx.car.widget.ListItemProvider;
import androidx.car.widget.PagedListView;
import androidx.car.widget.TextListItem;

import com.android.car.media.common.MediaItemMetadata;
import com.android.car.media.common.MediaSource;
import com.android.car.media.common.PlaybackControls;
import com.android.car.media.common.PlaybackModel;

import java.util.List;

/**
 * A {@link Fragment} that implements both the playback and the content forward browsing experience.
 * It observes a {@link PlaybackModel} and updates its information depending on the currently
 * playing media source through the {@link android.media.session.MediaSession} API.
 */
public class PlaybackFragment extends Fragment {
    private static final String TAG = "PlaybackFragment";

    private PlaybackModel mModel;
    private PlaybackControls mPlaybackControls;
    private QueueItemsAdapter mQueueAdapter;

    private MetadataController mMetadataController;
    private ConstraintLayout mRootView;

    private boolean mQueueIsVisible;
    private PlaybackModel.PlaybackObserver mPlaybackObserver =
            new PlaybackModel.PlaybackObserver() {
                @Override
                public void onPlaybackStateChanged() {
                    updateState();
                }

                @Override
                public void onSourceChanged() {
                    updateAccentColor();
                    updateState();
                }

                @Override
                public void onMetadataChanged() {
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

    private PlaybackControls.Listener mPlaybackControlsListener = new PlaybackControls.Listener() {
        @Override
        public void onToggleQueue() {
            mQueueIsVisible = !mQueueIsVisible;
            mPlaybackControls.setQueueVisible(mQueueIsVisible);
            setQueueVisible(mQueueIsVisible);
        }
    };

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, final ViewGroup container,
            Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_playback, container, false);
        mRootView = view.findViewById(R.id.playback_container);
        mModel = new PlaybackModel(getContext());

        initPlaybackControls(view.findViewById(R.id.playback_controls));
        initQueue(view.findViewById(R.id.queue_list));
        initMetadataController(view);
        return view;
    }

    private void initPlaybackControls(PlaybackControls playbackControls) {
        mPlaybackControls = playbackControls;
        mPlaybackControls.setModel(mModel);
        mPlaybackControls.setListener(mPlaybackControlsListener);
        mPlaybackControls.setAnimationViewGroup(mRootView);
    }

    private void initQueue(PagedListView queueList) {
        RecyclerView recyclerView = queueList.getRecyclerView();
        recyclerView.setVerticalFadingEdgeEnabled(true);
        recyclerView.setFadingEdgeLength(getResources()
                .getDimensionPixelSize(R.dimen.car_padding_4));
        mQueueAdapter = new QueueItemsAdapter(getContext(), mQueueItemsProvider);
        queueList.setAdapter(mQueueAdapter);
    }

    private void initMetadataController(View view) {
        ImageView albumArt = view.findViewById(R.id.album_art);
        TextView title = view.findViewById(R.id.title);
        TextView subtitle = view.findViewById(R.id.subtitle);
        SeekBar seekbar = view.findViewById(R.id.seek_bar);
        TextView time = view.findViewById(R.id.time);
        mMetadataController = new MetadataController(title, subtitle, time, seekbar, albumArt);
        mMetadataController.setModel(mModel);
    }


    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mPlaybackControls.setModel(null);
        mMetadataController.setModel(null);
        mMetadataController = null;
    }

    @Override
    public void onStart() {
        super.onStart();
        mModel.registerObserver(mPlaybackObserver);
    }

    @Override
    public void onStop() {
        super.onStop();
        mModel.unregisterObserver(mPlaybackObserver);
    }

    public void setQueueVisible(boolean visible) {
        Transition transition = TransitionInflater.from(getContext()).inflateTransition(
                visible ? R.transition.queue_in : R.transition.queue_out);
        TransitionManager.beginDelayedTransition(mRootView, transition);
        ConstraintSet constraintSet = new ConstraintSet();
        constraintSet.clone(mRootView.getContext(),
                visible ? R.layout.fragment_playback_with_queue : R.layout.fragment_playback);
        constraintSet.applyTo(mRootView);

    }

    private void updateState() {
        mQueueAdapter.refresh();
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
