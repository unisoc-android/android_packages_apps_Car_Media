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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.car.media.common.MediaConstants;
import com.android.car.media.common.MediaItemMetadata;

/**
 * Strategy used to group and expand media items in the {@link BrowseAdapter}
 */
public interface ContentForwardStrategy {
    /**
     * @return true if a header should be included when expanding the given media item into a
     * section. Only used if {@link #shouldBeExpanded(MediaItemMetadata)} returns true.
     */
    boolean includeHeader(@NonNull MediaItemMetadata mediaItem);

    /**
     * @return maximum number of rows to use when when expanding the given media item into a
     * section. The number can be different depending on the {@link BrowseItemViewType} that will be
     * used to represent media item children (i.e.: we might allow more rows for lists than for
     * grids). Only used if {@link #shouldBeExpanded(MediaItemMetadata)} returns true.
     */
    int getMaxRows(@NonNull MediaItemMetadata mediaItem, @NonNull BrowseItemViewType viewType);

    /**
     * @return whether the given media item should be expanded or not. If not expanded, the item
     * will be displayed according to its parent preferred view type.
     */
    boolean shouldBeExpanded(@NonNull MediaItemMetadata mediaItem);

    /**
     * @return view type to use to render browsable children of the given media item. Only used if
     * {@link #shouldBeExpanded(MediaItemMetadata)} returns true.
     */
    BrowseItemViewType getBrowsableViewType(@Nullable MediaItemMetadata mediaItem);

    /**
     * @return view type to use to render playable children fo the given media item. Only used if
     * {@link #shouldBeExpanded(MediaItemMetadata)} returns true.
     */
    BrowseItemViewType getPlayableViewType(@Nullable MediaItemMetadata mediaItem);

    /**
     * @return true if a "more" button should be displayed as a footer for a section displaying the
     * given media item, in case that there item has more children than the ones that can be
     * displayed according to {@link #getMaxQueueRows()}. Only used if {@link
     * #shouldBeExpanded(MediaItemMetadata)} returns true.
     */
    boolean showMoreButton(@NonNull MediaItemMetadata mediaItem);

    /**
     * @return maximum number of items to show for the media queue, if one is provided.
     */
    int getMaxQueueRows();

    /**
     * @return view type to use to display queue items.
     */
    BrowseItemViewType getQueueViewType();

    ContentForwardStrategy DEFAULT_STRATEGY = new ContentForwardStrategy() {

        @Override
        public boolean includeHeader(@NonNull MediaItemMetadata mediaItem) {
            return true;
        }

        @Override
        public int getMaxRows(@NonNull MediaItemMetadata mediaItem,
                @NonNull BrowseItemViewType viewType) {
            return viewType == BrowseItemViewType.GRID_ITEM ? 2 : 8;
        }

        @Override
        public boolean shouldBeExpanded(@NonNull MediaItemMetadata mediaItem) {
            return true;
        }

        @Override
        public BrowseItemViewType getBrowsableViewType(@Nullable MediaItemMetadata mediaItem) {
            if (mediaItem == null) {
                return BrowseItemViewType.PANEL_ITEM;
            }
            return (mediaItem.getBrowsableContentStyleHint()
                    == MediaConstants.CONTENT_STYLE_GRID_ITEM_HINT_VALUE)
                    ? BrowseItemViewType.PANEL_ITEM : BrowseItemViewType.LIST_ITEM;
        }

        @Override
        public BrowseItemViewType getPlayableViewType(@Nullable MediaItemMetadata mediaItem) {
            if (mediaItem == null) {
                return BrowseItemViewType.GRID_ITEM;
            }
            return (mediaItem.getPlayableContentStyleHint()
                    == MediaConstants.CONTENT_STYLE_GRID_ITEM_HINT_VALUE)
                    ? BrowseItemViewType.GRID_ITEM : BrowseItemViewType.LIST_ITEM;
        }

        @Override
        public boolean showMoreButton(@NonNull MediaItemMetadata mediaItem) {
            return false;
        }

        @Override
        public int getMaxQueueRows() {
            return 8;
        }

        @Override
        public BrowseItemViewType getQueueViewType() {
            return BrowseItemViewType.LIST_ITEM;
        }
    };
}
