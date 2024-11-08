/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;

import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;

import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.pm.UserCache;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * A Generic User Profile Manager which abstract outs the common functionality required
 * by user-profiles supported by Launcher
 * <p>
 * Concrete impls are
 * {@link com.android.launcher3.allapps.AllAppsWorkProfileManager} which manages work profile state
 * {@link com.android.launcher3.allapps.PrivateProfileManager} which manages private profile state.
 */
public abstract class UserProfileManager {
    private static final String TAG = UserProfileManager.class.getSimpleName();
    public static final int STATE_UNKNOWN = 0;
    public static final int STATE_ENABLED = 1;
    public static final int STATE_DISABLED = 2;
    public static final int STATE_TRANSITION = 3;

    @IntDef(value = {
            STATE_UNKNOWN,
            STATE_ENABLED,
            STATE_DISABLED,
            STATE_TRANSITION
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface UserProfileState { }

    protected final StatsLogManager mStatsLogManager;
    protected final UserManager mUserManager;
    protected final UserCache mUserCache;

    @UserProfileState
    private int mCurrentState;
    protected UserProfileManager(UserManager userManager,
            StatsLogManager statsLogManager,
            UserCache userCache) {
        mUserManager = userManager;
        mStatsLogManager = statsLogManager;
        mUserCache = userCache;
    }

    /**
     * Sets quiet mode as enabled/disabled for the first child profile user found that matches our
     * profile type, if any. At least one profile of our type must be present.
     */
    public final void setQuietMode(boolean enabled) {
        setQuietMode(enabled, getProfileUser());
    }

    /**
     * Sets quiet mode as enabled/disabled for the given UserHandle, which must be one of the
     * primary user's child profiles and must match our profile type.
     */
    protected void setQuietMode(boolean enabled, final @NonNull UserHandle user) {
        throwIfProfileNotOurs(user);
        UI_HELPER_EXECUTOR.post(() -> mUserManager.requestQuietModeEnabled(enabled, user));
    }

    protected boolean isProfileNotOurs(UserHandle user) {
        if (user == null) {
            Log.w(TAG, "[" + this.getClass().getSimpleName() + "] "
                    + "Failed to operate on user null", new Throwable());
            return true;
        }
        if (!mUserCache.getUserProfiles().contains(user)) {
            Log.w(TAG, "[" + this.getClass().getSimpleName() + "] "
                    + "Failed to operate on user " + user + ": "
                    + "Not one of our profiles", new Throwable());
            return true;
        }
        if (!getUserMatcher().test(user)) {
            Log.w(TAG, "[" + this.getClass().getSimpleName() + "] "
                    + "Failed to operate on user " + user + ": "
                    + "Not our profile type");
            return true;
        }
        return false;
    }

    protected final void throwIfProfileNotOurs(final @NonNull UserHandle user) {
        Objects.requireNonNull(user, "user must not be null");
        if (isProfileNotOurs(user)) {
            throw new SecurityException("user not ours: " + user);
        }
    }

    /** Sets current state for the profile type. */
    protected void setCurrentState(int state) {
        mCurrentState = state;
    }

    /** Returns current state for the profile type. */
    public int getCurrentState() {
        return mCurrentState;
    }

    /** Returns if user profile is enabled. */
    public boolean isEnabled() {
        return mCurrentState == STATE_ENABLED;
    }

    /**
     * Returns the first UserHandle found that corresponds to this profile type, or null
     * in case no matches found.
     */
    public UserHandle getProfileUser() {
        return mUserCache.getUserProfiles().stream()
                .filter(getUserMatcher())
                .findFirst()
                .orElse(null);
    }

    /** Logs Event to StatsLogManager. */
    protected void logEvents(StatsLogManager.EventEnum event) {
        mStatsLogManager.logger().log(event);
    }

    /** Returns the matcher corresponding to profile type. */
    protected abstract Predicate<UserHandle> getUserMatcher();

    /** Returns the matcher corresponding to the profile type associated with ItemInfo. */
    public Predicate<ItemInfo> getItemInfoMatcher() {
        return itemInfo -> itemInfo != null && getUserMatcher().test(itemInfo.user);
    }
}
