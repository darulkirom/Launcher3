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

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;

import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.pm.UserCache;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Hashtable;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Stream;

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
    public static final UserHandle PERSONAL_USER_HANDLE = Process.myUserHandle();
    public static final int STATE_UNKNOWN = 0;
    public static final int STATE_ENABLED = 1;
    public static final int STATE_DISABLED = 2;
    public static final int STATE_TRANSITION = 3;
    public static final int STATE_UNAVAILABLE = 4;

    @IntDef(value = {
            STATE_UNKNOWN,
            STATE_ENABLED,
            STATE_DISABLED,
            STATE_TRANSITION,
            STATE_UNAVAILABLE
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface UserProfileState { }

    protected final StatsLogManager mStatsLogManager;
    protected final UserManager mUserManager;
    protected final UserCache mUserCache;
    private final Hashtable<UserHandle, Integer> mUserStates = new Hashtable<>();
    @NonNull
    private List<UserHandle> mOurUsers;

    protected UserProfileManager(UserManager userManager,
            StatsLogManager statsLogManager,
            UserCache userCache) {
        mUserManager = userManager;
        mStatsLogManager = statsLogManager;
        mUserCache = userCache;

        // Run our handler with MODEL_EXECUTOR so that UserCache's user list is updated
        // before we react.
        mUserCache.addUserEventListener((userHandle, action) ->
                MODEL_EXECUTOR.execute(() -> onUserEvent(userHandle, action)));
        mUserCache.maybePerformInitialCacheUpdate();
        mOurUsers = getLatestProfileUsers();
    }

    private void onUserEvent(final UserHandle userHandle, final String action) {
        // The reason we do not directly assign mOurUsers to getLatestProfileUsers() here is that
        // we want to avoid a (theoretical) race condition in which multiple user events have
        // taken place before we were able to react. This mainly impacts removals. If a user was
        // added and another user was removed, but we are just now processing the addition first,
        // then if we replaced our list of users fully with the latest list, by the time the
        // removal was processed, we would no longer recognize the removed user as having been
        // one of ours, so its removal event would not fire.
        // On the other hand, if the same user was added and removed, and we are still processing
        // its addition, it will appear to not apply to us. This is fine. We do not need to react
        // to events for a user that was present so very briefly.
        if (UserCache.ACTION_PROFILE_REMOVED.equals(action)) {
            if (!mOurUsers.contains(userHandle)) {
                return;
            }
            // Remove user from our tracked list of users.
            mOurUsers = mOurUsers.stream().filter(u -> !u.equals(userHandle)).toList();
            mUserStates.remove(userHandle);
            MAIN_EXECUTOR.execute(() -> onUserRemoved(userHandle));
            return;
        }
        if (UserCache.ACTION_PROFILE_ADDED.equals(action)) {
            if (!getLatestProfileUsers().contains(userHandle)) {
                return;
            }
            // Add user to our tracked list of users.
            mOurUsers = Stream.concat(mOurUsers.stream(), Stream.of(userHandle)).toList();
            MAIN_EXECUTOR.execute(() -> onUserAdded(userHandle));
            return;
        }
        if (!mOurUsers.contains(userHandle)) {
            return;
        }
        if (UserCache.ACTION_PROFILE_LOCKED.equals(action)) {
            MAIN_EXECUTOR.execute(() -> onUserLocked(userHandle));
        } else if (UserCache.ACTION_PROFILE_UNLOCKED.equals(action)) {
            MAIN_EXECUTOR.execute(() -> onUserUnlocked(userHandle));
        }
    }

    @SuppressWarnings("unused")
    protected void onUserAdded(UserHandle userHandle) {
        // optionally implemented in subclasses
    }
    @SuppressWarnings("unused")
    protected void onUserRemoved(UserHandle userHandle) {
        // optionally implemented in subclasses
    }
    @SuppressWarnings("unused")
    protected void onUserLocked(UserHandle userHandle) {
        // optionally implemented in subclasses
    }
    @SuppressWarnings("unused")
    protected void onUserUnlocked(UserHandle userHandle) {
        // optionally implemented in subclasses
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
        if (!mOurUsers.contains(user)) {
            Log.w(TAG, "[" + this.getClass().getSimpleName() + "] "
                    + "Failed to operate on user " + user + ": "
                    + "Not one of our profiles", new Throwable());
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

    /** Sets current state for the user of this profile type. */
    protected void setCurrentState(final @NonNull UserHandle user, int state) {
        throwIfProfileNotOurs(user);
        mUserStates.put(user, state);
    }

    /** Returns current state for the user of this profile type. */
    public int getCurrentState(final @Nullable UserHandle user) {
        if (user == null || isProfileNotOurs(user)) {
            return STATE_UNAVAILABLE;
        }
        return mUserStates.getOrDefault(user, STATE_UNKNOWN);
    }

    /** Returns if the given user profile is enabled. */
    public final boolean isEnabled(final @NonNull UserHandle user) {
        return getCurrentState(user) == STATE_ENABLED;
    }

    /** Returns current state for the profile type. */
    public final int getCurrentState() {
        // TODO: Update tests and do away with this method.
        return getCurrentState(getProfileUser());
    }

    /** Returns if user profile is enabled. */
    public final boolean isEnabled() {
        // TODO: Update tests and do away with this method.
        return isEnabled(getProfileUser());
    }

    /**
     * Returns the first UserHandle found that corresponds to this profile type, or null
     * in case no matches found. This list of UserHandles is cached during initialization and
     * updated upon user change events.
     */
    public UserHandle getProfileUser() {
        return getProfileUsers().stream().findFirst().orElse(null);
    }

    /**
     * Returns an immutable list of UserHandles that corresponds to this profile type, or an empty
     * list in case no matches found. This value is cached during initialization and updated upon
     * user change events.
     */
    public List<UserHandle> getProfileUsers() {
        return mOurUsers;
    }

    private List<UserHandle> getLatestProfileUsers() {
        return mUserCache.getUserProfiles().stream()
                .filter(getUserMatcher())
                .toList();
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
