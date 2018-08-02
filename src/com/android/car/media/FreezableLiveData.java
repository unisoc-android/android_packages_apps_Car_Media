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

import static java.util.Objects.requireNonNull;

import android.annotation.NonNull;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;

/**
 * Utility for creating a LiveData that does not update when specified.
 */
public class FreezableLiveData<T> extends MediatorLiveData<T> {
    /**
     * Create a LiveData that doesn't change when {@code isFrozen} emits {@code true}. If {@code
     * source} has updated while the data was frozen, it will be updated to the current value once
     * unfrozen.
     *
     * @param isFrozen the result will not update while this data emits {@code true}.
     * @param source   the source data for the result.
     * @return a LiveData that doesn't change when {@code isFrozen} emits {@code true}.
     */
    @NonNull
    public static <T> LiveData<T> freezable(@NonNull LiveData<Boolean> isFrozen,
            @NonNull LiveData<T> source) {
        return new FreezableLiveData<>(isFrozen, source);
    }

    private boolean mDirty = false;

    private FreezableLiveData(@NonNull LiveData<Boolean> isFrozen,
            @NonNull LiveData<T> source) {
        addSource(requireNonNull(isFrozen), frozen -> {
            if (frozen == Boolean.FALSE && mDirty) {
                setValue(source.getValue());
                mDirty = false;
            }
        });
        addSource(requireNonNull(source), value -> {
            if (isFrozen.getValue() != Boolean.FALSE) {
                mDirty = true;
            } else {
                setValue(source.getValue());
                mDirty = false;
            }
        });

    }
}
