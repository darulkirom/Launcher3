/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.launcher3;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public abstract class BaseAdapterHolder<T extends RecyclerView.Adapter<?>> {
    /**
     * Primary profile, i.e. main profile. Value represents a type of adapter holder and index
     * in the adapter holder collection.
     */
    public static final int PRIMARY = 0;
    /**
     * Work profile. Value represents a type of adapter holder and index in the adapter holder
     * collection.
     */
    public static final int WORK = 1;
    /**
     * Search page. Value represents a type of adapter holder and index in the adapter holder
     * collection.
     */
    public static final int SEARCH = 2;

    public final int mAdapterType;
    public final T mAdapter;

    public BaseAdapterHolder(int adapterType, T adapter) {
        mAdapterType = adapterType;
        mAdapter = adapter;
    }

    public abstract void setup(@NonNull RecyclerView rv);
}
