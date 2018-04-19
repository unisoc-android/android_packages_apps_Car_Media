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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.android.car.media.browse.BrowseAdapter;
import com.android.car.media.browse.ContentForwardStrategy;
import com.android.car.media.common.GridSpacingItemDecoration;
import com.android.car.media.common.MediaItemMetadata;
import com.android.car.media.common.MediaSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import androidx.car.widget.PagedListView;

/**
 * A {@link Fragment} that implements the content forward browsing experience.
 */
public class BrowseFragment extends Fragment {
    private static final String TAG = "BrowseFragment";
    private static final String TOP_MEDIA_ITEM_KEY = "top_media_item";
    private static final String MEDIA_SOURCE_PACKAGE_NAME_KEY = "media_source";
    private static final String BROWSE_STACK_KEY = "browse_stack";

    private PagedListView mBrowseList;
    private MediaSource mMediaSource;
    private BrowseAdapter mBrowseAdapter;
    private String mMediaSourcePackageName;
    private MediaItemMetadata mTopMediaItem;
    private Callbacks mCallbacks;
    private Stack<MediaItemMetadata> mBrowseStack = new Stack<>();
    private MediaSource.Observer mBrowseObserver = new MediaSource.Observer() {
        @Override
        protected void onBrowseConnected(boolean success) {
            BrowseFragment.this.onBrowseConnected(success);
        }

        @Override
        protected void onBrowseDisconnected() {
            BrowseFragment.this.onBrowseDisconnected();
        }
    };
    private BrowseAdapter.Observer mBrowseAdapterObserver = new BrowseAdapter.Observer() {
        @Override
        protected void onDirty() {
            mBrowseAdapter.update();
            if (mBrowseAdapter.getItemCount() > 0) {
                mBrowseList.setVisibility(View.VISIBLE);
            } else {
                mBrowseList.setVisibility(View.GONE);
                // TODO(b/77647430) implement intermediate states.
            }
        }

        @Override
        protected void onPlayableItemClicked(MediaItemMetadata item) {
            mCallbacks.onPlayableItemClicked(mMediaSource, item);
        }

        @Override
        protected void onBrowseableItemClicked(MediaItemMetadata item) {
            navigateInto(item);
        }

        @Override
        protected void onMoreButtonClicked(MediaItemMetadata item) {
            navigateInto(item);
        }
    };

    /**
     * Fragment callbacks (implemented by the hosting Activity)
     */
    public interface Callbacks {
        /**
         * @return a {@link MediaSource} corresponding to the given package name
         */
        MediaSource getMediaSource(String packageName);

        /**
         * Method invoked when the back stack changes (for example, when the user moves up or down
         * the media tree)
         */
        void onBackStackChanged();

        /**
         * Method invoked when the user clicks on a playable item
         *
         * @param mediaSource {@link MediaSource} the playable item belongs to
         * @param item item to be played.
         */
        void onPlayableItemClicked(MediaSource mediaSource, MediaItemMetadata item);
    }

    /**
     * Moves the user one level up in the browse tree, if possible.
     */
    public void navigateBack() {
        mBrowseStack.pop();
        if (mBrowseAdapter != null) {
            mBrowseAdapter.setParentMediaItemId(getCurrentMediaItem());
        }
        if (mCallbacks != null) {
            mCallbacks.onBackStackChanged();
        }
    }

    /**
     * @return whether the user is in a level other than the top.
     */
    public boolean isBackEnabled() {
        return !mBrowseStack.isEmpty();
    }

    /**
     * Creates a new instance of this fragment.
     *
     * @param item media tree node to display on this fragment.
     * @return a fully initialized {@link BrowseFragment}
     */
    public static BrowseFragment newInstance(MediaSource mediaSource, MediaItemMetadata item) {
        BrowseFragment fragment = new BrowseFragment();
        Bundle args = new Bundle();
        args.putParcelable(TOP_MEDIA_ITEM_KEY, item);
        args.putString(MEDIA_SOURCE_PACKAGE_NAME_KEY, mediaSource.getPackageName());
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle arguments = getArguments();
        if (arguments != null) {
            mTopMediaItem = arguments.getParcelable(TOP_MEDIA_ITEM_KEY);
            mMediaSourcePackageName = arguments.getString(MEDIA_SOURCE_PACKAGE_NAME_KEY);
        }
        if (savedInstanceState != null) {
            List<MediaItemMetadata> savedStack =
                    savedInstanceState.getParcelableArrayList(BROWSE_STACK_KEY);
            mBrowseStack.clear();
            if (savedStack != null) {
                mBrowseStack.addAll(savedStack);
            }
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, final ViewGroup container,
            Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_browse, container, false);
        mBrowseList = view.findViewById(R.id.browse_list);
        int numColumns = getContext().getResources().getInteger(R.integer.num_browse_columns);
        GridLayoutManager gridLayoutManager = new GridLayoutManager(getContext(), numColumns);
        RecyclerView recyclerView = mBrowseList.getRecyclerView();
        recyclerView.setVerticalFadingEdgeEnabled(true);
        recyclerView.setFadingEdgeLength(getResources()
                .getDimensionPixelSize(R.dimen.car_padding_4));
        recyclerView.setLayoutManager(gridLayoutManager);
        recyclerView.addItemDecoration(new GridSpacingItemDecoration(
                getResources().getDimensionPixelSize(R.dimen.car_padding_4),
                getResources().getDimensionPixelSize(R.dimen.car_keyline_1),
                getResources().getDimensionPixelSize(R.dimen.car_keyline_1)
        ));
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

    @Override
    public void onStart() {
        super.onStart();
        mMediaSource = mCallbacks.getMediaSource(mMediaSourcePackageName);
        if (mMediaSource != null) {
            mMediaSource.subscribe(mBrowseObserver);
        }
        if (mBrowseAdapter != null) {
            mBrowseAdapter.start();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mMediaSource != null) {
            mMediaSource.unsubscribe(mBrowseObserver);
        }
        if (mBrowseAdapter != null) {
            mBrowseAdapter.stop();
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        ArrayList<MediaItemMetadata> stack = new ArrayList<>(mBrowseStack);
        outState.putParcelableArrayList(BROWSE_STACK_KEY, stack);
    }

    private void onBrowseConnected(boolean success) {
        if (mBrowseAdapter != null) {
            mBrowseAdapter.stop();
            mBrowseAdapter = null;
        }
        if (!success) {
            mBrowseList.setVisibility(View.GONE);
            // TODO(b/77647430) implement intermediate states.
            return;
        }
        mBrowseAdapter = new BrowseAdapter(getContext(), mMediaSource.getMediaBrowser(),
                getCurrentMediaItem(), ContentForwardStrategy.DEFAULT_STRATEGY);
        mBrowseList.setAdapter(mBrowseAdapter);
        mBrowseAdapter.registerObserver(mBrowseAdapterObserver);
        mBrowseAdapter.start();
    }

    private void onBrowseDisconnected() {
        if (mBrowseAdapter != null) {
            mBrowseAdapter.stop();
            mBrowseAdapter = null;
        }
    }

    private void navigateInto(MediaItemMetadata item) {
        mBrowseStack.push(item);
        mBrowseAdapter.setParentMediaItemId(item);
        mCallbacks.onBackStackChanged();
    }

    /**
     * @return the current item being displayed
     */
    public MediaItemMetadata getCurrentMediaItem() {
        if (mBrowseStack.isEmpty()) {
            return mTopMediaItem;
        } else {
            return mBrowseStack.lastElement();
        }
    }
}
