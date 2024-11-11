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

import android.os.UserHandle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
    /**
     * The index of the first additional work profile (i.e. the second work profile) in the
     * adapter holder collection. Any additional work profiles follow this index sequentially.
     */
    public static final int ADDITIONAL_WORK_ADAPTER_HOLDER_START = 3;

    public static final int PRIMARY_PAGE = 0;
    public static final int WORK_PAGE = 1;
    public static final int ADDITIONAL_WORK_PAGE_START = 2;

    public final int mAdapterType;
    public final T mAdapter;
    public final UserHandle mUserHandle;

    public BaseAdapterHolder(int adapterType, T adapter, UserHandle userHandle) {
        mAdapterType = adapterType;
        mAdapter = adapter;
        mUserHandle = userHandle;
    }

    public abstract void setup(@NonNull RecyclerView rv);

    public void applyPadding() {
        // do nothing by default
    }

    public static int getPageForType(int type) {
        return switch (type) {
            case PRIMARY -> PRIMARY_PAGE;
            case WORK -> WORK_PAGE;
            case SEARCH ->
                    throw new IllegalArgumentException("SEARCH does not have a pager index");
            default ->
                    throw new IllegalArgumentException("No pager index found for type " + type);
        };
    }

    public static int getTypeForPage(int page) {
        if (page == PRIMARY_PAGE) {
            return PRIMARY;
        }
        if (page == WORK_PAGE) {
            return WORK;
        }
        if (page >= ADDITIONAL_WORK_PAGE_START) {
            return WORK;
        }
        throw new IllegalArgumentException("No type found for page " + page);
    }

    public static int getPageForAdapterHolderIndex(int index) {
        if (index == WORK) {
            return WORK_PAGE;
        }
        if (index <= PRIMARY || index == SEARCH) {
            return PRIMARY_PAGE;
        }
        // View pager does not contain a search view, so fold it in.
        return index - 1;
    }

    public static int getAdapterHolderIndexForPage(int page) {
        if (page == WORK_PAGE) {
            return WORK;
        }
        if (page <= PRIMARY_PAGE) {
            return PRIMARY;
        }
        // Leave a gap for the search adapter holder, which does not have a page.
        return page + 1;
    }

    @Nullable
    public abstract RecyclerView getRecyclerView();
}
