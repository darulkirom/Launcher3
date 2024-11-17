package com.android.launcher3;

import static com.android.launcher3.BaseAdapterHolder.ADDITIONAL_WORK_ADAPTER_HOLDER_START;
import static com.android.launcher3.BaseAdapterHolder.ADDITIONAL_WORK_PAGE_START;
import static com.android.launcher3.BaseAdapterHolder.PRIMARY;
import static com.android.launcher3.BaseAdapterHolder.PRIMARY_PAGE;
import static com.android.launcher3.BaseAdapterHolder.WORK;
import static com.android.launcher3.BaseAdapterHolder.WORK_PAGE;
import static com.android.launcher3.BaseAdapterHolder.getPageForType;
import static com.android.launcher3.Flags.enableMultipleWorkTabs;

import android.content.Context;
import android.content.res.Resources;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.LayoutRes;
import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.workprofile.PersonalWorkPagedView;
import com.android.launcher3.workprofile.PersonalWorkSlidingTabStrip;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class WorkProfileManager extends UserProfileManager {
    private static final String TAG = WorkProfileManager.class.getSimpleName();
    protected final PersonalWorkTabFrontend<?> mFrontend;
    protected UserHandle mCurrentWorkUser;
    @Nullable
    private String mPersonalTabLabel;
    @Nullable private String mPersonalTabContentDescription;
    @Nullable private String mWorkTabLabel;
    @Nullable private String mWorkTabContentDescription;
    @LayoutRes
    private int mWorkTabResId = Resources.ID_NULL;
    private PersonalWorkSlidingTabStrip mTabBar;
    private boolean mShouldRebuildWorkUI = true;

    public WorkProfileManager(UserManager userManager,
            PersonalWorkTabFrontend<?> frontend,
            StatsLogManager statsLogManager,
            UserCache userCache) {
        super(userManager, statsLogManager, userCache);
        mFrontend = frontend;
    }

    public void setTabBar(final @Nullable PersonalWorkSlidingTabStrip tabBar) {
        mTabBar = tabBar;
        if (tabBar != null) {
            tabBar.requireViewById(R.id.tab_personal)
                    .setTag(R.id.userhandle_tag, PERSONAL_USER_HANDLE);
            tabBar.requireViewById(R.id.tab_work)
                    .setTag(R.id.userhandle_tag, getProfileUser());
        }
    }

    @Override
    public Predicate<UserHandle> getUserMatcher() {
        return this::isWorkProfile;
    }

    private boolean isWorkProfile(final UserHandle userHandle) {
        return mUserCache.getUserInfo(userHandle).isWork();
    }

    public void removeWorkUI() {
        removeTabs();
        removeAdapterHoldersAndRecyclerViews();
        mShouldRebuildWorkUI = true;
    }

    @Override
    public UserHandle getProfileUser() {
        if (mCurrentWorkUser == null) {
            final int pagerIndex = mFrontend.getCurrentPagerIndex();
            final UserHandle adapterHolderWorkUser = getWorkUserForPage(pagerIndex);
            mCurrentWorkUser = adapterHolderWorkUser == null
                    ? super.getProfileUser() : adapterHolderWorkUser;
        }
        return mCurrentWorkUser;
    }

    private UserHandle getWorkUserForPage(int pagerIndex) {
        BaseAdapterHolder<?> adapterHolder = mFrontend.getAdapterHolders().get(
                BaseAdapterHolder.getAdapterHolderIndexForPage(pagerIndex));
        return (adapterHolder != null && adapterHolder.mAdapterType == WORK)
                ? adapterHolder.mUserHandle : null;
    }

    /**
     * Updates work profile states and updates work profile related views
     */
    public final void reset() {
        if (mShouldRebuildWorkUI) {
            removeWorkUI();
            rebuildWorkUI();
            mShouldRebuildWorkUI = false;
        }
        updateQuietStates();
        onReset();
    }

    /**
     * Additional tasks to perform when {@link #reset} is called.
     * Called after user states have been updated via {@link #updateQuietStates}.
     */
    protected void onReset() {
        // can be overridden.
        // otherwise, do nothing.
    }

    /**
     * Set the state for each work profile based on its current quiet mode status.
     */
    protected void updateQuietStates() {
        for (final UserHandle user : getProfileUsers()) {
            if (mUserManager.isQuietModeEnabled(user)) {
                setCurrentState(user, STATE_DISABLED);
            } else {
                setCurrentState(user, STATE_ENABLED);
            }
        }
    }

    /**
     * Rebuilds elements related to the work UI, removing any existing ones.
     */
    public void rebuildWorkUI() {
        final List<UserHandle> workUsers = getProfileUsers();
        rebuildTabBar(workUsers);
        rebuildAdapterHoldersAndRecyclerViews(workUsers);
    }

    private void removeTabs() {
        final PersonalWorkSlidingTabStrip tabBar = mTabBar;
        if (!enableMultipleWorkTabs() || tabBar == null) {
            return;
        }
        final int numChildren = tabBar.getChildCount();
        if (numChildren > ADDITIONAL_WORK_PAGE_START) {
            tabBar.removeViews(ADDITIONAL_WORK_PAGE_START, tabBar.getChildCount() - 1);
        }
        if (numChildren > WORK_PAGE) {
            tabBar.getChildAt(WORK_PAGE).setTag(R.id.userhandle_tag, null);
        }
    }

    private void rebuildTabBar(final @NonNull List<UserHandle> workUsers) {
        final PersonalWorkSlidingTabStrip tabBar = mTabBar;
        if (tabBar == null) {
            Log.w(TAG, "Tried to rebuild tab bar, but we have none");
            return;
        }
        tabBar.requireViewById(R.id.tab_personal)
                .setTag(R.id.userhandle_tag, PERSONAL_USER_HANDLE);
        if (enableMultipleWorkTabs()) {
            for (final UserHandle user : workUsers) {
                addTab(tabBar, user);
            }
        }
    }

    private void removeAdapterHoldersAndRecyclerViews() {
        final PersonalWorkPagedView pagedView = mFrontend.getPagedView();
        if (!enableMultipleWorkTabs() || pagedView == null) {
            return;
        }
        final int numPages = pagedView.getChildCount();
        if (numPages > WORK_PAGE) {
            pagedView.removeViews(WORK_PAGE, numPages - WORK_PAGE);
        }
        final List<? extends BaseAdapterHolder<?>> adapterHolders =
                mFrontend.getAdapterHolders();
        final int numAdapterHolders = adapterHolders.size();
        adapterHolders.set(WORK, null);
        if (numAdapterHolders > ADDITIONAL_WORK_ADAPTER_HOLDER_START) {
            adapterHolders.subList(ADDITIONAL_WORK_ADAPTER_HOLDER_START, numAdapterHolders)
                    .clear();
        }
        addAndSetupAdapterHolderAndRecyclerView(pagedView, /*userHandle*/ null);
    }

    private void rebuildAdapterHoldersAndRecyclerViews(final @NonNull List<UserHandle> workUsers) {
        if (!enableMultipleWorkTabs()) {
            return;
        }
        final PersonalWorkPagedView pagedView = mFrontend.getPagedView();
        if (pagedView == null) {
            return;
        }
        pagedView.getChildAt(PRIMARY_PAGE).setTag(
                R.id.userhandle_tag,
                PERSONAL_USER_HANDLE
        );
        for (final UserHandle user : workUsers) {
            addAndSetupAdapterHolderAndRecyclerView(pagedView, user);
        }
    }

    public void setFallbackLabels(
            final String personalTab,
            final String personalTabAccessibility,
            final String workTab,
            final String workTabAccessibility
    ) {
        mPersonalTabLabel = personalTab;
        mPersonalTabContentDescription = personalTabAccessibility;
        mWorkTabLabel = workTab;
        mWorkTabContentDescription = workTabAccessibility;
        final PersonalWorkSlidingTabStrip tabBar = mTabBar;
        if (tabBar == null) {
            // TODO: Add test.
            Log.w(TAG, "Tried to set fallback labels, but we have no tab bar");
            return;
        }
        final int numTabs = tabBar.getChildCount();
        for (int i = 0; i < numTabs; i++) {
            updateTabLabel(tabBar, (Button) tabBar.getChildAt(i));
        }
    }

    private void updateTabLabel(final PersonalWorkSlidingTabStrip tabBar, final Button tab) {
        final UserHandle tagUserHandle = (UserHandle) tab.getTag(R.id.userhandle_tag);
        final UserHandle userHandle;
        Log.i(TAG, "Updating tab label on tab " + tab + " with user tag " + tagUserHandle);
        if (tagUserHandle != null) {
            userHandle = tagUserHandle;
        } else {
            if (enableMultipleWorkTabs()) {
                Log.e(TAG, "Could not get UserHandle for tab while updating labels: " + tab);
                return;
            } else {
                userHandle = getProfileUser();
            }
        }
        if (PERSONAL_USER_HANDLE.equals(userHandle)) {
            Optional.ofNullable(mPersonalTabLabel)
                    .ifPresent(tab::setText);
            Optional.ofNullable(mPersonalTabContentDescription)
                    .ifPresent(tab::setContentDescription);
            return;
        }
        String label = null;
        String contentDescription = null;
        if (enableMultipleWorkTabs()) {
            // Try to set customized tab labels if available (e.g. 'Work 2').
            final Context userContext = tabBar.getContext().createContextAsUser(userHandle,
                    /*flags*/ 0);
            final UserManager userManagerAsUser =
                    userContext.getSystemService(UserManager.class);
            if (userManagerAsUser != null) {
                label = tryGetOrNull(userManagerAsUser::getProfileLabel,
                        "profile label for " + userHandle);
                contentDescription = tryGetOrNull(
                        () -> userManagerAsUser.getProfileAccessibilityString(
                                userHandle.getIdentifier()),
                        "profile content description for " + userHandle);
            }
        }
        label = label == null ? mWorkTabLabel : label;
        if (label != null) {
            tab.setText(label);
        }
        contentDescription = contentDescription == null
                ? mWorkTabContentDescription : contentDescription;
        if (contentDescription != null) {
            tab.setContentDescription(contentDescription);
        }
    }

    private static <T> T tryGetOrNull(Supplier<T> supplier, String item) {
        try {
            return supplier.get();
        } catch (Resources.NotFoundException e) {
            Log.e(TAG, "Failed to get " + item, e);
            return null;
        }
    }

    public void onUserAdded(final UserHandle userHandle) {
        if (enableMultipleWorkTabs()) {
            final PersonalWorkSlidingTabStrip tabBar = mTabBar;
            if (tabBar == null) {
                // TODO: Add test.
                Log.w(TAG, "Tried to add user tab, but we have no tab bar");
                return;
            }
            addTab(tabBar, userHandle);
            addAndSetupAdapterHolderAndRecyclerView(
                    mFrontend.getPagedView(),
                    userHandle);
        }
    }

    public void onUserRemoved(final UserHandle userHandle) {
        if (userHandle.equals(mCurrentWorkUser)) {
            mCurrentWorkUser = null;
        }
        if (enableMultipleWorkTabs()) {
            final PersonalWorkSlidingTabStrip tabBar = mTabBar;
            if (tabBar == null) {
                // TODO: Add test.
                Log.w(TAG, "Tried to remove user tab, but we have no tab bar");
                return;
            }
            PersonalWorkPagedView pagedView = mFrontend.getPagedView();
            if (pagedView != null) {
                removeTab(tabBar, userHandle);
                mFrontend.removeAdapterHolderAndRecyclerView(
                        pagedView,
                        userHandle);
                if (mFrontend.getCurrentPagerIndex() >= pagedView.getChildCount()) {
                    pagedView.setCurrentPage(pagedView.getChildCount() - 1);
                }
            }
        }
    }

    public void addTab(final PersonalWorkSlidingTabStrip tabBar, final UserHandle userHandle) {
        final Button existingWorkTab = (Button) tabBar.getChildAt(WORK_PAGE);
        final Button tab;
        if (existingWorkTab != null && existingWorkTab.getTag(R.id.userhandle_tag) == null) {
            tab = existingWorkTab;
            final int resId = tab.getSourceLayoutResId();
            if (resId != Resources.ID_NULL) {
                mWorkTabResId = tab.getSourceLayoutResId();
            }
        } else {
            tab = (Button) LayoutInflater.from(tabBar.getContext())
                    .inflate(mWorkTabResId, tabBar, false);
        }
        tab.setTag(R.id.userhandle_tag, userHandle);
        tab.setOnClickListener(mFrontend::onTabClicked);
        updateTabLabel(tabBar, tab);
        if (tab != existingWorkTab) {
            tabBar.addView(tab);
        }
    }

    @SuppressWarnings("UnusedReturnValue")
    private BaseAdapterHolder<?> addAndSetupAdapterHolderAndRecyclerView(
            final @NonNull PersonalWorkPagedView pagedView,
            final @Nullable UserHandle userHandle) {
        final List<? extends BaseAdapterHolder<?>> adapterHolders = mFrontend.getAdapterHolders();
        final BaseAdapterHolder<?> existingWorkAdapterHolder = adapterHolders.get(WORK);
        if (existingWorkAdapterHolder != null && existingWorkAdapterHolder.mUserHandle == null) {
            final RecyclerView existingWorkRV = existingWorkAdapterHolder.getRecyclerView();
            if (existingWorkRV != null) {
                pagedView.removeView(existingWorkRV);
            }
        }
        final RecyclerView recyclerView = mFrontend.createRecyclerView();
        recyclerView.setTag(R.id.userhandle_tag, userHandle);
        pagedView.addView(recyclerView);
        final BaseAdapterHolder<?> adapterHolder =
                mFrontend.addNewWorkAdapterHolder(userHandle);
        adapterHolder.setup(recyclerView);
        return adapterHolder;
    }

    public boolean removeTab(
            final PersonalWorkSlidingTabStrip tabBar,
            final UserHandle userHandle) {
        return removeUserViewFromGroup(tabBar, userHandle);
    }

    private boolean removeUserViewFromGroup(
            final @NonNull ViewGroup viewGroup,
            final UserHandle userHandle) {
        final int numChildren = viewGroup.getChildCount();
        for (int i = 1; i < numChildren; i++) {
            final UserHandle thisChildUserHandle =
                    (UserHandle) viewGroup.getChildAt(i).getTag(R.id.userhandle_tag);
            if (thisChildUserHandle == null) {
                continue;
            }
            if (thisChildUserHandle.equals(userHandle)) {
                viewGroup.removeViewAt(i);
                return true;
            }
        }
        return false;
    }

    public int getPageForUserHandle(UserHandle userHandle) {
        final PersonalWorkPagedView viewPager = mFrontend.getPagedView();
        if (viewPager == null || PERSONAL_USER_HANDLE.equals(userHandle)) {
            return getPageForType(PRIMARY);
        } else if (userHandle != null) {
            for (int i = 0; i < viewPager.getChildCount(); i++) {
                final View recyclerView = viewPager.getChildAt(i);
                final UserHandle thisUserHandle =
                        (UserHandle) recyclerView.getTag(R.id.userhandle_tag);
                if (userHandle.equals(thisUserHandle)) {
                    return i;
                }
            }
        }
        return getPageForType(WORK);
    }

    public interface PersonalWorkTabFrontend<T extends BaseAdapterHolder<?>> {
        void onTabClicked(View tab);
        PersonalWorkPagedView getPagedView();
        RecyclerView createRecyclerView();
        @NonNull List<T> getAdapterHolders();
        int getCurrentPagerIndex();
        int getCurrentAdapterHolderIndex();

        /**
         * Create a work adapter.
         *
         * @param userHandle The UserHandle to use as a filter for the work adapter. If null,
         *                   this method should set the item filter to the default all-inclusive
         *                   work item filter.
         * @return The created work adapter holder.
         */
        T createWorkAdapterHolder(@Nullable UserHandle userHandle);

        default T addNewWorkAdapterHolder(@Nullable UserHandle userHandle) {
            final List<T> adapterHolders = getAdapterHolders();
            final T newAdapterHolder = createWorkAdapterHolder(userHandle);
            final T existingWorkAdapterHolder = adapterHolders.get(WORK);
            if (existingWorkAdapterHolder == null
                    || existingWorkAdapterHolder.mUserHandle == null) {
                adapterHolders.set(WORK, newAdapterHolder);
                if (existingWorkAdapterHolder != null) {
                    existingWorkAdapterHolder.close();
                }
            } else {
                if (adapterHolders.size() < ADDITIONAL_WORK_ADAPTER_HOLDER_START) {
                    // We expect a minimum number of adapters at all times. If it is less, then we
                    // would be adding the new work adapter to the wrong position.
                    // TODO: Add test.
                    throw new IllegalStateException("Unexpected adapters count "
                            + adapterHolders.size() + "; unsupported removal likely");
                }
                adapterHolders.add(newAdapterHolder);
            }
            return newAdapterHolder;
        }

        @SuppressWarnings("UnusedReturnValue")
        default boolean removeAdapterHolderAndRecyclerView(
                final PersonalWorkPagedView pagedView,
                final UserHandle userHandle) {
            Objects.requireNonNull(userHandle, "userHandle must not be null");
            final List<T> adapterHolders = getAdapterHolders();
            for (int i = 0; i < adapterHolders.size(); i++) {
                final T adapterHolder = adapterHolders.get(i);
                if (userHandle.equals(adapterHolder.mUserHandle)) {
                    pagedView.removeView(adapterHolder.getRecyclerView());
                    if (i == WORK) {
                        if (adapterHolders.size() > ADDITIONAL_WORK_ADAPTER_HOLDER_START) {
                            adapterHolders.set(i,
                                    adapterHolders.get(ADDITIONAL_WORK_ADAPTER_HOLDER_START));
                            adapterHolders.remove(ADDITIONAL_WORK_ADAPTER_HOLDER_START);
                        } else {
                            adapterHolders.set(i, createWorkAdapterHolder(/*userHandle*/ null));
                        }
                    } else {
                        adapterHolders.remove(i);
                    }
                    return true;
                }
            }
            // TODO: Add test.
            Log.e(TAG, "Tried to remove views for user " + userHandle + " but not found among "
                    + adapterHolders);
            return false;
        }
    }
}
