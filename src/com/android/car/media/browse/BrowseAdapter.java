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

package com.android.car.media.browse;

import static java.util.stream.Collectors.toList;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.car.widget.PagedListView;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.android.car.media.common.MediaItemMetadata;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * A {@link RecyclerView.Adapter} that can be used to display a single level of a {@link
 * android.service.media.MediaBrowserService} media tree into a {@link
 * androidx.car.widget.PagedListView} or any other {@link RecyclerView}.
 *
 * <p>This adapter assumes that the attached {@link RecyclerView} uses a {@link GridLayoutManager},
 * as it can use both grid and list elements to produce the desired representation.
 *
 * <p> The actual strategy to group and expand media items has to be supplied by providing an
 * instance of {@link ContentForwardStrategy}.
 *
 * <p>Consumers of this adapter should use {@link #registerObserver(Observer)} to receive updates.
 */
public class BrowseAdapter extends ListAdapter<BrowseViewData, BrowseViewHolder> implements
        PagedListView.DividerVisibilityManager {
    private static final String TAG = "BrowseAdapter";
    @NonNull
    private final Context mContext;
    @NonNull
    private final ContentForwardStrategy mCFBStrategy;
    @NonNull
    private List<Observer> mObservers = new ArrayList<>();
    @Nullable
    private CharSequence mTitle;
    @Nullable
    private MediaItemMetadata mParentMediaItem;
    private int mMaxSpanSize = 1;

    private static final DiffUtil.ItemCallback<BrowseViewData> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<BrowseViewData>() {
                @Override
                public boolean areItemsTheSame(@NonNull BrowseViewData oldItem,
                        @NonNull BrowseViewData newItem) {
                    return Objects.equals(oldItem.mMediaItem, newItem.mMediaItem)
                            && Objects.equals(oldItem.mText, newItem.mText);
                }

                @Override
                public boolean areContentsTheSame(@NonNull BrowseViewData oldItem,
                        @NonNull BrowseViewData newItem) {
                    return oldItem.equals(newItem);
                }
            };

    /**
     * Possible states of the adapter
     */
    public enum State {
        /** Loading of this item hasn't started yet */
        IDLE,
        /** There is pending information before this item can be displayed */
        LOADING,
        /** It was not possible to load metadata for this item */
        ERROR,
        /** Metadata for this items has been correctly loaded */
        LOADED
    }

    /**
     * An {@link BrowseAdapter} observer.
     */
    public static abstract class Observer {

        /**
         * Callback invoked when a user clicks on a playable item.
         */
        protected void onPlayableItemClicked(MediaItemMetadata item) {
        }

        /**
         * Callback invoked when a user clicks on a browsable item.
         */
        protected void onBrowseableItemClicked(MediaItemMetadata item) {
        }

        /**
         * Callback invoked when a user clicks on a the "more items" button on a section.
         */
        protected void onMoreButtonClicked(MediaItemMetadata item) {
        }

        /**
         * Callback invoked when the user clicks on the title of the queue.
         */
        protected void onTitleClicked() {
        }

    }

    /**
     * Represents the loading state of children of a single {@link MediaItemMetadata} in the {@link
     * BrowseAdapter}
     */
    private class MediaItemState {
        /**
         * {@link com.android.car.media.common.MediaItemMetadata} whose children are being loaded
         */
        final MediaItemMetadata mItem;
        /** Playable children of the given item */
        List<MediaItemMetadata> mPlayableChildren = new ArrayList<>();
        /** Browsable children of the given item */
        List<MediaItemMetadata> mBrowsableChildren = new ArrayList<>();

        MediaItemState(MediaItemMetadata item) {
            mItem = item;
        }

        void setChildren(List<MediaItemMetadata> children) {
            mPlayableChildren.clear();
            mBrowsableChildren.clear();
            for (MediaItemMetadata child : children) {
                if (child.isBrowsable()) {
                    // Browsable items could also be playable
                    mBrowsableChildren.add(child);
                } else if (child.isPlayable()) {
                    mPlayableChildren.add(child);
                }
            }
        }
    }

    /**
     * Creates a {@link BrowseAdapter} that displays the children of the given media tree node.
     *
     * @param strategy a {@link ContentForwardStrategy} that would determine which items would be
     *                 expanded and how.
     */
    public BrowseAdapter(@NonNull Context context, @NonNull ContentForwardStrategy strategy) {
        super(DIFF_CALLBACK);
        mContext = context;
        mCFBStrategy = strategy;
    }

    /**
     * Sets title to be displayed.
     */
    public void setTitle(CharSequence title) {
        mTitle = title;
    }

    /**
     * Registers an {@link Observer}
     */
    public void registerObserver(Observer observer) {
        mObservers.add(observer);
    }

    /**
     * Unregisters an {@link Observer}
     */
    public void unregisterObserver(Observer observer) {
        mObservers.remove(observer);
    }

    /**
     * Sets the number of columns that items can take. This method only needs to be used if the
     * attached {@link RecyclerView} is NOT using a {@link GridLayoutManager}. This class will
     * automatically determine this value on {@link #onAttachedToRecyclerView(RecyclerView)}
     * otherwise.
     */
    public void setMaxSpanSize(int maxSpanSize) {
        mMaxSpanSize = maxSpanSize;
    }

    /**
     * @return a {@link GridLayoutManager.SpanSizeLookup} that can be used to obtain the span size
     * of each item in this adapter. This method is only needed if the {@link RecyclerView} is NOT
     * using a {@link GridLayoutManager}. This class will automatically use it on\ {@link
     * #onAttachedToRecyclerView(RecyclerView)} otherwise.
     */
    private GridLayoutManager.SpanSizeLookup getSpanSizeLookup() {
        return new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                BrowseItemViewType viewType = getItem(position).mViewType;
                return viewType.getSpanSize(mMaxSpanSize);
            }
        };
    }

    @NonNull
    @Override
    public BrowseViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layoutId = BrowseItemViewType.values()[viewType].getLayoutId();
        View view = LayoutInflater.from(mContext).inflate(layoutId, parent, false);
        return new BrowseViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BrowseViewHolder holder, int position) {
        BrowseViewData viewData = getItem(position);
        holder.bind(mContext, viewData);
    }

    @Override
    public int getItemViewType(int position) {
        return getItem(position).mViewType.ordinal();
    }

    public void submitItems(@Nullable MediaItemMetadata parentItem,
            @Nullable List<MediaItemMetadata> children) {
        mParentMediaItem = parentItem;
        List<MediaItemState> mediaItemStates =
                children == null ? Collections.emptyList()
                        : children.stream()
                                .map(MediaItemState::new)
                                .collect(toList());
        submitList(generateViewData(mediaItemStates));
    }

    private void notify(Consumer<Observer> notification) {
        for (Observer observer : mObservers) {
            notification.accept(observer);
        }
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        if (recyclerView.getLayoutManager() instanceof GridLayoutManager) {
            GridLayoutManager manager = (GridLayoutManager) recyclerView.getLayoutManager();
            mMaxSpanSize = manager.getSpanCount();
            manager.setSpanSizeLookup(getSpanSizeLookup());
        }
    }

    private class ItemsBuilder {
        private List<BrowseViewData> result = new ArrayList<>();

        void addItem(MediaItemMetadata item,
                BrowseItemViewType viewType, Consumer<Observer> notification) {
            View.OnClickListener listener = notification != null ?
                    view -> BrowseAdapter.this.notify(notification) :
                    null;
            result.add(new BrowseViewData(item, viewType, listener));
        }

        void addItems(List<MediaItemMetadata> items, BrowseItemViewType viewType, int maxRows) {
            int spanSize = viewType.getSpanSize(mMaxSpanSize);
            int maxChildren = maxRows * (mMaxSpanSize / spanSize);
            result.addAll(items.stream()
                    .limit(maxChildren)
                    .map(item -> {
                        Consumer<Observer> notification =
                                item.isBrowsable()
                                        ? observer -> observer.onBrowseableItemClicked(item)
                                        : observer -> observer.onPlayableItemClicked(item);
                        return new BrowseViewData(item, viewType, view ->
                                BrowseAdapter.this.notify(notification));
                    })
                    .collect(toList()));
        }

        void addTitle(CharSequence title, Consumer<Observer> notification) {
            result.add(new BrowseViewData(title, BrowseItemViewType.HEADER,
                    view -> BrowseAdapter.this.notify(notification)));

        }

        void addBrowseBlock(MediaItemMetadata header,
                List<MediaItemMetadata> items, BrowseItemViewType viewType, int maxChildren,
                boolean showHeader, boolean showMoreFooter) {
            if (showHeader) {
                addItem(header, BrowseItemViewType.HEADER, null);
            }
            addItems(items, viewType, maxChildren);
            if (showMoreFooter) {
                addItem(header, BrowseItemViewType.MORE_FOOTER,
                        observer -> observer.onMoreButtonClicked(header));
            }
        }

        List<BrowseViewData> build() {
            return result;
        }
    }

    /**
     * Flatten the given collection of item states into a list of {@link BrowseViewData}s. To avoid
     * flickering, the flatting will stop at the first "loading" section, avoiding unnecessary
     * insertion animations during the initial data load.
     */
    private List<BrowseViewData> generateViewData(Collection<MediaItemState> itemStates) {
        ItemsBuilder itemsBuilder = new ItemsBuilder();

        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "Generating browse view from:");
            for (MediaItemState item : itemStates) {
                Log.v(TAG, String.format("[%s%s] '%s' (%s)",
                        item.mItem.isBrowsable() ? "B" : " ",
                        item.mItem.isPlayable() ? "P" : " ",
                        item.mItem.getTitle(),
                        item.mItem.getId()));
                List<MediaItemMetadata> items = new ArrayList<>();
                items.addAll(item.mBrowsableChildren);
                items.addAll(item.mPlayableChildren);
                for (MediaItemMetadata child : items) {
                    Log.v(TAG, String.format("   [%s%s] '%s' (%s)",
                            child.isBrowsable() ? "B" : " ",
                            child.isPlayable() ? "P" : " ",
                            child.getTitle(),
                            child.getId()));
                }
            }
        }

        if (mTitle != null) {
            itemsBuilder.addTitle(mTitle, Observer::onTitleClicked);
        }

        boolean containsBrowsableItems = false;
        boolean containsPlayableItems = false;
        for (MediaItemState itemState : itemStates) {
            containsBrowsableItems |= itemState.mItem.isBrowsable();
            containsPlayableItems |= itemState.mItem.isPlayable();
        }

        for (MediaItemState itemState : itemStates) {
            MediaItemMetadata item = itemState.mItem;
            if (containsPlayableItems && containsBrowsableItems) {
                // If we have a mix of browsable and playable items: show them all in a list
                itemsBuilder.addItem(item,
                        BrowseItemViewType.PANEL_ITEM,
                        item.isBrowsable()
                                ? observer -> observer.onBrowseableItemClicked(item)
                                : observer -> observer.onPlayableItemClicked(item));
            } else if (itemState.mItem.isBrowsable()) {
                // If we only have browsable items, check whether we should expand them or not.
                if (!itemState.mBrowsableChildren.isEmpty()
                        && !itemState.mPlayableChildren.isEmpty()
                        || !mCFBStrategy.shouldBeExpanded(item)) {
                    itemsBuilder.addItem(item,
                            mCFBStrategy.getBrowsableViewType(mParentMediaItem), null);
                } else if (!itemState.mPlayableChildren.isEmpty()) {
                    itemsBuilder.addBrowseBlock(item,
                            itemState.mPlayableChildren,
                            mCFBStrategy.getPlayableViewType(item),
                            mCFBStrategy.getMaxRows(item, mCFBStrategy.getPlayableViewType(item)),
                            mCFBStrategy.includeHeader(item),
                            mCFBStrategy.showMoreButton(item));
                } else if (!itemState.mBrowsableChildren.isEmpty()) {
                    itemsBuilder.addBrowseBlock(item,
                            itemState.mBrowsableChildren,
                            mCFBStrategy.getBrowsableViewType(item),
                            mCFBStrategy.getMaxRows(item, mCFBStrategy.getBrowsableViewType(item)),
                            mCFBStrategy.includeHeader(item),
                            mCFBStrategy.showMoreButton(item));
                }
            } else if (item.isPlayable()) {
                // If we only have playable items: show them as so.
                itemsBuilder.addItem(item,
                        mCFBStrategy.getPlayableViewType(mParentMediaItem),
                        observer -> observer.onPlayableItemClicked(item));
            }
        }

        return itemsBuilder.build();
    }

    @Override
    public boolean getShowDivider(int position) {
        return (position < getItemCount() - 1
                && position >= 0
                && getItem(position).mViewType == BrowseItemViewType.PANEL_ITEM
                && getItem(position + 1).mViewType == BrowseItemViewType.PANEL_ITEM);
    }
}
