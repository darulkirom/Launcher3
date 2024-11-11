/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.launcher3.allapps;

import static com.android.launcher3.BaseAdapterHolder.ADDITIONAL_WORK_ADAPTER_HOLDER_START;
import static com.android.launcher3.BaseAdapterHolder.PRIMARY;
import static com.android.launcher3.BaseAdapterHolder.SEARCH;
import static com.android.launcher3.BaseAdapterHolder.WORK;
import static com.android.launcher3.Flags.enableMultipleWorkTabs;
import static com.android.launcher3.LauncherPrefs.WORK_EDU_STEP;
import static com.android.launcher3.allapps.BaseAllAppsAdapter.VIEW_TYPE_WORK_DISABLED_CARD;
import static com.android.launcher3.allapps.BaseAllAppsAdapter.VIEW_TYPE_WORK_EDU_CARD;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_TURN_OFF_WORK_APPS_TAP;
import static com.android.launcher3.model.BgDataModel.Callbacks.FLAG_HAS_SHORTCUT_PERMISSION;
import static com.android.launcher3.model.BgDataModel.Callbacks.FLAG_QUIET_MODE_CHANGE_PERMISSION;
import static com.android.launcher3.model.BgDataModel.Callbacks.FLAG_QUIET_MODE_ENABLED;
import static com.android.launcher3.model.BgDataModel.Callbacks.FLAG_WORK_PROFILE_QUIET_MODE_ENABLED;

import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.BaseAdapterHolder;
import com.android.launcher3.Flags;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.WorkProfileManager;
import com.android.launcher3.allapps.BaseAllAppsAdapter.AdapterItem;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.workprofile.PersonalWorkPagedView;

import java.util.ArrayList;
import java.util.stream.Stream;

/**
 * Companion class for {@link ActivityAllAppsContainerView} to manage work tab and personal tab
 * related
 * logic based on {@link UserProfileState}?
 */
public class AllAppsWorkProfileManager extends WorkProfileManager {
    private static final String TAG = "WorkProfileManager";
    private final ActivityAllAppsContainerView<?> mAllApps;
    private WorkModeSwitch mWorkModeSwitch;

    public AllAppsWorkProfileManager(
            UserManager userManager, ActivityAllAppsContainerView allApps,
            StatsLogManager statsLogManager, UserCache userCache) {
        super(userManager, allApps, statsLogManager, userCache);
        mAllApps = allApps;
    }

    /**
     * Posts quiet mode enable/disable call for the first work profile user found, if any.
     */
    public void setWorkProfileEnabled(boolean enabled) {
        setWorkProfileEnabled(enabled, getProfileUser());
    }

    /**
     * Posts quiet mode enable/disable call for the given work profile user.
     */
    public void setWorkProfileEnabled(boolean enabled, final @NonNull UserHandle workUser) {
        setCurrentState(workUser, STATE_TRANSITION);
        updateViews(workUser);
        setQuietMode(!enabled, workUser);
    }

    public void updateWorkFAB(int adapterHolderIndex) {
        // Ensure the adapter holder index actually represents a work profile with an associated
        // user handle. If so, store the user handle as the current user. If the profile is enabled
        // (not quiet mode), show the work mode switch.
        // Otherwise, hide the switch.
        final BaseAdapterHolder<?> adapterHolder =
                mFrontend.getAdapterHolders().get(adapterHolderIndex);
        if (adapterHolder != null && adapterHolder.mAdapterType == WORK) {
            final UserHandle userHandle;
            if (adapterHolder.mUserHandle != null) {
                userHandle = adapterHolder.mUserHandle;
                mCurrentWorkUser = adapterHolder.mUserHandle;
            } else {
                if (enableMultipleWorkTabs()) {
                    // When multiple work tabs are enabled, we expect that the UserHandle is set.
                    // If it's not, we don't want to do anything, here.
                    userHandle = null;
                    Log.e(TAG, "updateWorkFAB for " + adapterHolderIndex + ", no user!");
                } else {
                    userHandle = getProfileUser();
                }
            }
            if (userHandle != null && isEnabled(userHandle)
                    && (mWorkModeSwitch != null || attachWorkModeSwitch())) {
                mWorkModeSwitch.animateVisibility(true);
                return;
            }
        }
        if (mWorkModeSwitch != null) {
            mWorkModeSwitch.animateVisibility(false);
        }
    }

    @Override
    protected void onReset() {
        getProfileUsers().forEach(this::updateViews);
        if (mWorkModeSwitch != null) {
            // reset the position of the button and clear IME insets.
            mWorkModeSwitch.getImeInsets().setEmpty();
            mWorkModeSwitch.updateTranslationY();
        }
    }

    /**
     * Requests work profile states from {@link AllAppsStore}
     */
    @Override
    public void updateQuietStates() {
        int quietModeFlag;
        if (Flags.enablePrivateSpace()) {
            quietModeFlag = FLAG_WORK_PROFILE_QUIET_MODE_ENABLED;
        } else {
            quietModeFlag = FLAG_QUIET_MODE_ENABLED;
        }
        for (final UserHandle user : getProfileUsers()) {
            boolean isEnabled =
                    !mAllApps.getAppsStore().hasModelUserFlag(user, quietModeFlag);
            setCurrentState(user, isEnabled ? STATE_ENABLED : STATE_DISABLED);
        }
    }

    /**
     * Updates work profile-related views for the given user
     */
    private void updateViews(final @NonNull UserHandle user) {
        if (getAH(user) instanceof ActivityAllAppsContainerView<?>.AdapterHolder allAppsAH) {
            allAppsAH.mAppsList.updateAdapterItems();
        }
        if (user.equals(getProfileUser())) {
            if (mWorkModeSwitch != null) {
                updateWorkFAB(mFrontend.getCurrentAdapterHolderIndex());
            }
            if (isEnabled(user)) {
                attachWorkModeSwitch();
            } else {
                detachWorkModeSwitch();
            }
        }
    }

    /**
     * Creates and attaches for profile toggle button to {@link ActivityAllAppsContainerView}
     */
    public boolean attachWorkModeSwitch() {
        if (!mAllApps.getAppsStore().hasModelFlag(
                FLAG_HAS_SHORTCUT_PERMISSION | FLAG_QUIET_MODE_CHANGE_PERMISSION)) {
            Log.e(TAG, "unable to attach work mode switch; Missing required permissions");
            return false;
        }
        if (mWorkModeSwitch == null) {
            mWorkModeSwitch = (WorkModeSwitch) mAllApps.getLayoutInflater().inflate(
                    R.layout.work_mode_fab, mAllApps, false);
        }
        if (mWorkModeSwitch.getParent() == null) {
            mAllApps.addView(mWorkModeSwitch);
        }
        if (mAllApps.getCurrentAdapterHolderType() != WORK) {
            mWorkModeSwitch.animateVisibility(false);
        }
        if (getAH() != null) {
            getAH().applyPadding();
        }
        mWorkModeSwitch.setOnClickListener(this::onWorkFabClicked);
        return true;
    }
    /**
     * Removes work profile toggle button from {@link ActivityAllAppsContainerView}
     */
    public void detachWorkModeSwitch() {
        if (mWorkModeSwitch != null && mWorkModeSwitch.getParent() == mAllApps) {
            mAllApps.removeView(mWorkModeSwitch);
        }
        mWorkModeSwitch = null;
    }

    @Nullable
    public WorkModeSwitch getWorkModeSwitch() {
        return mWorkModeSwitch;
    }

    private BaseAdapterHolder<?> getAH(final @NonNull UserHandle workUser) {
        return mFrontend.getAdapterHolders().stream()
                .filter(it -> workUser.equals(it.mUserHandle))
                .findFirst()
                .orElse(null);
    }

    private BaseAdapterHolder<?> getAH() {
        final PersonalWorkPagedView pagedView = mFrontend.getPagedView();
        if (pagedView != null && enableMultipleWorkTabs()) {
            final int adapterIndex =
                    BaseAdapterHolder.getAdapterHolderIndexForPage(pagedView.getNextPage());
            if (adapterIndex >= ADDITIONAL_WORK_ADAPTER_HOLDER_START) {
                return mFrontend.getAdapterHolders().get(adapterIndex);
            }
        }
        return mFrontend.getAdapterHolders().get(WORK);
    }

    /**
     * returns whether or not work apps should be visible in work tab for this user.
     */
    public boolean shouldShowWorkApps(final UserHandle workUser) {
        return getCurrentState(workUser) != STATE_DISABLED;
    }

    public boolean hasWorkApps() {
        return Stream.of(mAllApps.getAppsStore().getApps()).anyMatch(getItemInfoMatcher());
    }

    /**
     * Adds work profile specific adapter items to adapterItems and returns number of items added
     */
    public int addWorkItems(final UserHandle workUser, ArrayList<AdapterItem> adapterItems) {
        if (getCurrentState(workUser) == STATE_DISABLED) {
            //add disabled card here.
            adapterItems.add(new AdapterItem(VIEW_TYPE_WORK_DISABLED_CARD));
        } else if (getCurrentState(workUser) == STATE_ENABLED && !isEduSeen()) {
            adapterItems.add(new AdapterItem(VIEW_TYPE_WORK_EDU_CARD));
        }
        return adapterItems.size();
    }

    private boolean isEduSeen() {
        return LauncherPrefs.get(mAllApps.getContext()).get(WORK_EDU_STEP) != 0;
    }

    private void onWorkFabClicked(View view) {
        final UserHandle user = getProfileUser();
        if (isEnabled(user) && mWorkModeSwitch.isEnabled()) {
            logEvents(LAUNCHER_TURN_OFF_WORK_APPS_TAP);
            setWorkProfileEnabled(false, user);
        }
    }

    public RecyclerView.OnScrollListener newScrollListener() {
        return new RecyclerView.OnScrollListener() {
            int totalDelta = 0;
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState){
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    totalDelta = 0;
                }
            }
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                WorkModeSwitch fab = getWorkModeSwitch();
                if (fab == null){
                    return;
                }
                totalDelta = Utilities.boundToRange(totalDelta,
                        -fab.getScrollThreshold(), fab.getScrollThreshold()) + dy;
                boolean isScrollAtTop = recyclerView.computeVerticalScrollOffset() == 0;
                if ((isScrollAtTop || totalDelta < -fab.getScrollThreshold())) {
                    fab.extend();
                } else if (totalDelta > fab.getScrollThreshold()) {
                    fab.shrink();
                }
            }
        };
    }
}
