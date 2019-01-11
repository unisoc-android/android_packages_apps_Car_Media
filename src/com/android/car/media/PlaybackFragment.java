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

import static com.android.car.arch.common.LiveDataFunctions.dataOf;
import static com.android.car.arch.common.LiveDataFunctions.freezable;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.transition.Transition;
import android.transition.TransitionInflater;
import android.transition.TransitionListenerAdapter;
import android.transition.TransitionManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.car.widget.ListItem;
import androidx.car.widget.ListItemAdapter;
import androidx.car.widget.ListItemProvider;
import androidx.car.widget.PagedListView;
import androidx.car.widget.TextListItem;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.RecyclerView;

import com.android.car.media.common.MediaItemMetadata;
import com.android.car.media.common.PlaybackControls;
import com.android.car.media.common.playback.PlaybackViewModel;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link Fragment} that implements both the playback and the content forward browsing experience.
 * It observes a {@link PlaybackViewModel} and updates its information depending on the currently
 * playing media source through the {@link android.media.session.MediaSession} API.
 */
public class PlaybackFragment extends Fragment {
    private static final String TAG = "PlaybackFragment";

    private PlaybackControls mPlaybackControls;
    private QueueItemsAdapter mQueueAdapter;
    private PagedListView mQueue;
    private Callbacks mCallbacks;

    private MetadataController mMetadataController;
    private ConstraintLayout mRootView;

    private PlaybackViewModel.PlaybackController mController;
    private Long mActiveQueueItemId;

    private MutableLiveData<Boolean> mUpdatesPaused = dataOf(false);

    private boolean mQueueIsVisible;
    private List<MediaItemMetadata> mQueueItems = new ArrayList<>();
    private ListItemProvider mQueueItemsProvider = new ListItemProvider() {
        @Override
        public ListItem get(int position) {
            if (position < 0 || position >= mQueueItems.size()) {
                return null;
            }
            MediaItemMetadata item = mQueueItems.get(position);
            TextListItem textListItem = new TextListItem(getContext());
            textListItem.setTitle(item.getTitle() != null ? item.getTitle().toString() : null);
            textListItem.setBody(item.getSubtitle() != null ? item.getSubtitle().toString() : null);
            textListItem.setOnClickListener(v -> onQueueItemClicked(item));
            if (mActiveQueueItemId != null && mActiveQueueItemId == item.getQueueId()) {
                textListItem.setSupplementalIcon(R.drawable.ic_equalizer, false);
            }

            return textListItem;
        }

        @Override
        public int size() {
            return mQueueItems.size();
        }
    };

    private class QueueItemsAdapter extends ListItemAdapter {
        QueueItemsAdapter(Context context, ListItemProvider itemProvider) {
            super(context, itemProvider, BackgroundStyle.SOLID);
            setHasStableIds(true);
        }

        void refresh() {
            // TODO: Perform a diff between current and new content and trigger the proper
            // RecyclerView updates.
            this.notifyDataSetChanged();
        }

        @Override
        public long getItemId(int position) {
            return mQueueItems.get(position).getQueueId();
        }
    }

    /**
     * Callbacks this fragment can trigger
     */
    public interface Callbacks {
        /**
         * Indicates that the "show queue" button has been clicked
         */
        void onQueueButtonClicked();
    }

    private PlaybackControls.Listener mPlaybackControlsListener = new PlaybackControls.Listener() {
        @Override
        public void onToggleQueue() {
            if (mCallbacks != null) {
                mCallbacks.onQueueButtonClicked();
            }
        }
    };

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, final ViewGroup container,
            Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_playback, container, false);
        mRootView = view.findViewById(R.id.playback_container);
        mQueue = view.findViewById(R.id.queue_list);

        getPlaybackViewModel().getPlaybackController().observe(getViewLifecycleOwner(),
                controller -> mController = controller);
        initPlaybackControls(view.findViewById(R.id.playback_controls));
        initQueue(mQueue);
        initMetadataController(view);
        return view;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mCallbacks = (Callbacks) context;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mCallbacks = null;
    }

    private void initPlaybackControls(PlaybackControls playbackControls) {
        mPlaybackControls = playbackControls;
        mPlaybackControls.setModel(getPlaybackViewModel(), getViewLifecycleOwner());
        mPlaybackControls.setListener(mPlaybackControlsListener);
        mPlaybackControls.setAnimationViewGroup(mRootView);
    }

    private void initQueue(PagedListView queueList) {
        RecyclerView recyclerView = queueList.getRecyclerView();
        recyclerView.setVerticalFadingEdgeEnabled(true);
        recyclerView.setFadingEdgeLength(getResources()
                .getDimensionPixelSize(R.dimen.car_padding_4));
        mQueueAdapter = new QueueItemsAdapter(getContext(), mQueueItemsProvider);
        getPlaybackViewModel().getPlaybackInfo().getActiveQueueItemId().observe(
                getViewLifecycleOwner(), id -> {
                    mActiveQueueItemId = id;
                    mQueueAdapter.refresh();
                });
        queueList.setAdapter(mQueueAdapter);
        freezable(mUpdatesPaused, getPlaybackViewModel().getQueue()).observe(this, this::setQueue);
    }

    private void setQueue(List<MediaItemMetadata> queueItems) {
        mQueueItems = queueItems;
        mQueueAdapter.refresh();
    }

    private void initMetadataController(View view) {
        ImageView albumArt = view.findViewById(R.id.album_art);
        TextView title = view.findViewById(R.id.title);
        TextView subtitle = view.findViewById(R.id.subtitle);
        SeekBar seekbar = view.findViewById(R.id.seek_bar);
        TextView time = view.findViewById(R.id.time);
        mMetadataController = new MetadataController(getViewLifecycleOwner(),
                getPlaybackViewModel(), mUpdatesPaused,
                title, subtitle, time, seekbar, albumArt);
    }

    /**
     * Hides or shows the playback queue
     */
    public void toggleQueueVisibility() {
        mQueueIsVisible = !mQueueIsVisible;
        mPlaybackControls.setQueueVisible(mQueueIsVisible);

        Transition transition = TransitionInflater.from(getContext()).inflateTransition(
                mQueueIsVisible ? R.transition.queue_in : R.transition.queue_out);
        transition.addListener(new TransitionListenerAdapter() {

            @Override
            public void onTransitionStart(Transition transition) {
                super.onTransitionStart(transition);
                mUpdatesPaused.setValue(true);
            }

            @Override
            public void onTransitionEnd(Transition transition) {
                mUpdatesPaused.setValue(false);
                mQueue.getRecyclerView().scrollToPosition(0);
            }
        });
        TransitionManager.beginDelayedTransition(mRootView, transition);
        ConstraintSet constraintSet = new ConstraintSet();
        constraintSet.clone(mRootView.getContext(),
                mQueueIsVisible ? R.layout.fragment_playback_with_queue
                        : R.layout.fragment_playback);
        constraintSet.applyTo(mRootView);
    }

    private void onQueueItemClicked(MediaItemMetadata item) {
        if (mController != null) {
            mController.skipToQueueItem(item.getQueueId());
        }
    }

    /**
     * Collapses the playback controls.
     */
    public void closeOverflowMenu() {
        mPlaybackControls.close();
    }

    private PlaybackViewModel getPlaybackViewModel() {
        return ViewModelProviders.of(getActivity()).get(PlaybackViewModel.class);
    }
}
