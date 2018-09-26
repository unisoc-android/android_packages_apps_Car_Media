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

import androidx.annotation.NonNull;
import androidx.car.drawer.CarDrawerAdapter;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import androidx.recyclerview.widget.RecyclerView;

import com.android.car.arch.common.LifecycleFunctions;

/**
 * A CarDrawerAdapter that exposes a {@link Lifecycle}. This lifecycle is bounded by a parent
 * Lifecycle, and will only be {@link Lifecycle.State#RESUMED} when the adapter is attached to a
 * RecyclerView.
 */
public abstract class LifecycleDrawerAdapter extends CarDrawerAdapter implements LifecycleOwner {

    private LifecycleRegistry lifecycleRegistry = new LifecycleRegistry(this);
    private LifecycleOwner intersection;

    protected LifecycleDrawerAdapter(Context context,
            @NonNull Lifecycle parentLifecycle,
            boolean showDisabledListOnEmpty) {
        super(context, showDisabledListOnEmpty);
        lifecycleRegistry.markState(Lifecycle.State.CREATED);
        intersection = LifecycleFunctions.lesserOf(parentLifecycle, lifecycleRegistry);
    }

    @NonNull
    @Override
    public Lifecycle getLifecycle() {
        return intersection.getLifecycle();
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        lifecycleRegistry.markState(Lifecycle.State.RESUMED);
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);
        lifecycleRegistry.markState(Lifecycle.State.CREATED);
    }
}
