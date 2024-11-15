package com.android.launcher3;

import android.os.UserHandle;
import android.os.UserManager;

import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.pm.UserCache;

import java.util.function.Predicate;

public class WorkProfileManager extends UserProfileManager {
    public WorkProfileManager(UserManager userManager,
            StatsLogManager statsLogManager,
            UserCache userCache) {
        super(userManager, statsLogManager, userCache);
    }

    @Override
    public Predicate<UserHandle> getUserMatcher() {
        return this::isWorkProfile;
    }

    private boolean isWorkProfile(final UserHandle userHandle) {
        return mUserCache.getUserInfo(userHandle).isWork();
    }
}
