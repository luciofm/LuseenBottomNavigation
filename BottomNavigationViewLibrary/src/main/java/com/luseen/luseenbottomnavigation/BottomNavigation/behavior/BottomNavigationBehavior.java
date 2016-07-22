package com.luseen.luseenbottomnavigation.BottomNavigation.behavior;

/*
 * BottomNavigationLayout library for Android
 * Copyright (c) 2016. Nikola Despotoski (http://github.com/NikolaDespotoski).
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *
 */

import android.content.Context;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.ViewPropertyAnimatorCompat;
import android.support.v4.view.ViewPropertyAnimatorUpdateListener;
import android.support.v4.view.animation.LinearOutSlowInInterpolator;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.Interpolator;

import com.luseen.luseenbottomnavigation.BottomNavigation.BottomNavigationView;

import java.util.HashMap;

import static android.util.Log.DEBUG;
import static android.util.Log.ERROR;
import static android.util.Log.INFO;
import static android.util.Log.VERBOSE;
import static android.util.Log.WARN;

/**
 * Created by Nikola D. on 3/15/2016.
 */
public final class BottomNavigationBehavior<V extends View> extends VerticalScrollingBehavior<V> {
    private static final Interpolator INTERPOLATOR = new LinearOutSlowInInterpolator();
    private static final String TAG = "BottomNavigationBehavior";

    private boolean hidden = false;
    private ViewPropertyAnimatorCompat mOffsetValueAnimator;
    private int mSnackbarHeight = -1;
    private boolean scrollingEnabled = true;
    private boolean hideAlongSnackbar = false;
    /**
     * current Y offset
     */
    private int offset;
    private int scaledTouchSlop = 16; //Default, from ViewConfiguration.java PAGING_TOUCH_SLOP

    final HashMap<View, DependentView> dependentViewHashMap = new HashMap<>();
    FabDependentView fabDependentView;
    SnackBarDependentView snackbarDependentView;
    private OnExpandStatusChangeListener listener;
    private int height;
    private int bottomInset;
    private boolean translucentNavigation;
    private int maxOffset;

    public BottomNavigationBehavior() {
        super();
    }

    public BottomNavigationBehavior(Context context, AttributeSet attrs) {
        super(context, attrs);
        scaledTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop() * 2;
    }

    public static <V extends View> BottomNavigationBehavior<V> from(@NonNull V view) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (!(params instanceof CoordinatorLayout.LayoutParams)) {
            throw new IllegalArgumentException("The view is not a child of CoordinatorLayout");
        }
        CoordinatorLayout.Behavior behavior = ((CoordinatorLayout.LayoutParams) params)
                .getBehavior();
        if (!(behavior instanceof BottomNavigationBehavior)) {
            throw new IllegalArgumentException(
                    "The view is not associated with BottomNavigationBehavior");
        }
        return (BottomNavigationBehavior<V>) behavior;
    }

    public void setLayoutValues(final int bottomNavHeight, final int bottomInset) {
        log(TAG, INFO, "setLayoutValues(%d, %d)", bottomNavHeight, bottomInset);
        this.height = bottomNavHeight;
        this.bottomInset = bottomInset;
        this.translucentNavigation = bottomInset > 0;
        this.maxOffset = height + (translucentNavigation ? bottomInset : 0);
        //log(TAG, DEBUG, "height: %d, translucent: %b, maxOffset: %d, bottomInset: %d", height, translucentNavigation, maxOffset, bottomInset);
    }

    protected boolean isFloatingActionButton(View dependency) {
        return FloatingActionButton.class.isInstance(dependency);
    }

    protected boolean isSnackBar(View dependency) {
        return Snackbar.SnackbarLayout.class.isInstance(dependency);
    }

    @Override
    public boolean layoutDependsOn(CoordinatorLayout parent, V child, View dependency) {
        return isFloatingActionButton(dependency) || isSnackBar(dependency);
    }

    @Override
    public void onDependentViewRemoved(CoordinatorLayout parent, V child, View dependency) {
        log(TAG, ERROR, "onDependentViewRemoved(%s)", dependency.getClass().getSimpleName());

        if (isFloatingActionButton(dependency)) {
            fabDependentView = null;
        } else if (Snackbar.SnackbarLayout.class.isInstance(dependency)) {
            snackbarDependentView = null;
            if (null != fabDependentView) {
                fabDependentView.onDependentViewChanged(parent, child);
            }
        }

        final DependentView dependent = dependentViewHashMap.remove(dependency);
        log(TAG, ERROR, "removed: %s", dependent);
        if (null != dependent) {
            dependent.onDestroy();
        }
    }

    @Override
    public boolean onDependentViewChanged(CoordinatorLayout parent, V child, View dependency) {
        boolean isFab = isFloatingActionButton(dependency);
        boolean isSnackBack = isSnackBar(dependency);

        DependentView dependent;

        if (!dependentViewHashMap.containsKey(dependency)) {
            if (!isFab && !isSnackBack) {
                dependent = new GenericDependentView(dependency, height, bottomInset);
            } else if (isFab) {
                dependent = new FabDependentView(dependency, height, bottomInset);
                fabDependentView = (FabDependentView) dependent;
            } else {
                dependent = new SnackBarDependentView((Snackbar.SnackbarLayout) dependency, height, bottomInset);
                snackbarDependentView = (SnackBarDependentView) dependent;
            }
            dependentViewHashMap.put(dependency, dependent);
        } else {
            dependent = dependentViewHashMap.get(dependency);
        }

        if (null != dependent) {
            return dependent.onDependentViewChanged(parent, child);
        }

        return true;
    }


    @Override
    public boolean onStartNestedScroll(CoordinatorLayout coordinatorLayout, V child, View directTargetChild, View target, int nestedScrollAxes) {
        offset = 0;
        return super.onStartNestedScroll(coordinatorLayout, child, directTargetChild, target, nestedScrollAxes);
    }

    @Override
    public void onStopNestedScroll(CoordinatorLayout coordinatorLayout, V child, View target) {
        super.onStopNestedScroll(coordinatorLayout, child, target);
        offset = 0;
    }

    @Override
    public void onNestedVerticalOverScroll(CoordinatorLayout coordinatorLayout, V child, @ScrollDirection int direction, int currentOverScroll, int totalOverScroll) {
    }

    @Override
    public void onDirectionNestedPreScroll(CoordinatorLayout coordinatorLayout, V child, View target, int dx, int dy, int[] consumed, @ScrollDirection int scrollDirection) {
        offset += dy;
        if (offset < -scaledTouchSlop) {
            handleDirection(coordinatorLayout, child, scrollDirection);
            offset = 0;
        } else if (offset > scaledTouchSlop) {
            handleDirection(coordinatorLayout, child, scrollDirection);
            offset = 0;
        }
    }

    private void handleDirection(CoordinatorLayout coordinatorLayout, V child, @ScrollDirection int scrollDirection) {
        if (!scrollingEnabled) return;
        if (scrollDirection == ScrollDirection.SCROLL_DIRECTION_DOWN && hidden) {
            hidden = false;
            animateOffset(coordinatorLayout, child, 0);
        } else if (scrollDirection == ScrollDirection.SCROLL_DIRECTION_UP && !hidden) {
            hidden = true;
            animateOffset(coordinatorLayout, child, child.getHeight());
        }
    }

    @Override
    protected boolean onNestedDirectionFling(CoordinatorLayout coordinatorLayout, V child, View target, float velocityX, float velocityY, @ScrollDirection int scrollDirection) {
        if (Math.abs(velocityY) > 1000) {
            handleDirection(coordinatorLayout, child, scrollDirection);
        }
        return true;
    }

    private void animateOffset(CoordinatorLayout coordinatorLayout, final V child, final int offset) {
        ensureOrCancelAnimator(coordinatorLayout, child);
        mOffsetValueAnimator.translationY(offset).start();
    }

    private void ensureOrCancelAnimator(final CoordinatorLayout coordinatorLayout, final V child) {
        if (mOffsetValueAnimator == null) {
            mOffsetValueAnimator = ViewCompat.animate(child);
            mOffsetValueAnimator.setDuration(100);
            mOffsetValueAnimator.setInterpolator(INTERPOLATOR);
            mOffsetValueAnimator.setUpdateListener(new ViewPropertyAnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(View view) {
                    if (null != fabDependentView) {
                        fabDependentView.onDependentViewChanged(coordinatorLayout, child);
                    }
                    if (null != snackbarDependentView) {
                        snackbarDependentView.onDependentViewChanged(coordinatorLayout, child);
                    }

                }
            });
        } else {
            mOffsetValueAnimator.cancel();
        }
    }

    private interface BottomNavigationWithSnackbar {
        void updateSnackbar(CoordinatorLayout parent, View dependency, View child);
    }

    abstract static class DependentView<V extends View> {
        final V child;
        final ViewGroup.MarginLayoutParams layoutParams;
        final int bottomMargin;
        final int height;
        final int bottomInset;

        DependentView(V child, final int height, final int bottomInset) {
            this.child = child;
            this.layoutParams = (ViewGroup.MarginLayoutParams) child.getLayoutParams();
            this.bottomMargin = layoutParams.bottomMargin;
            this.height = height;
            this.bottomInset = bottomInset;
        }

        void onDestroy() { }

        abstract boolean onDependentViewChanged(CoordinatorLayout parent, View navigation);
    }

    static class GenericDependentView extends DependentView<View> {
        private static final String TAG = BottomNavigationBehavior.TAG + "." + GenericDependentView.class.getSimpleName();

        GenericDependentView(final View child, final int height, final int bottomInset) {
            super(child, height, bottomInset);
            log(TAG, INFO, "new GenericDependentView(%s)", child.getClass().getSimpleName());
        }

        @Override
        void onDestroy() { }

        @Override
        boolean onDependentViewChanged(final CoordinatorLayout parent, final View navigation) {
            log(TAG, VERBOSE, "onDependentViewChanged");
            layoutParams.bottomMargin = bottomMargin + height;
            return true;
        }
    }

    private static class FabDependentView extends DependentView<View> {
        private static final String TAG = BottomNavigationBehavior.TAG + "." + FabDependentView.class.getSimpleName();

        FabDependentView(final View child, final int height, final int bottomInset) {
            super(child, height, bottomInset);
            log(TAG, INFO, "new FabDependentView");
        }

        @Override
        boolean onDependentViewChanged(final CoordinatorLayout parent, final View navigation) {
            final float t = Math.max(0, navigation.getTranslationY() - height);

            log(TAG, DEBUG, "translationY: %f", navigation.getTranslationY());
            int transY = (int) navigation.getTranslationY();
            if (bottomInset > 0) {
                layoutParams.bottomMargin = (int) (bottomMargin + height - t);
            } else {
                layoutParams.bottomMargin = bottomMargin + height - transY;
            }
            child.requestLayout();
            return true;
        }

        @Override
        void onDestroy() { }
    }

    private static class SnackBarDependentView extends DependentView<Snackbar.SnackbarLayout> {
        private static final String TAG = BottomNavigationBehavior.TAG + "." + SnackBarDependentView.class.getSimpleName();
        private int snackbarHeight = -1;

        SnackBarDependentView(final Snackbar.SnackbarLayout child, final int height, final int bottomInset) {
            super(child, height, bottomInset);
        }

        @Override
        boolean onDependentViewChanged(final CoordinatorLayout parent, final View navigation) {
            log(TAG, VERBOSE, "onDependentViewChanged");

            if (Build.VERSION.SDK_INT < 21) {
                int index1 = parent.indexOfChild(child);
                int index2 = parent.indexOfChild(navigation);
                if (index1 > index2) {
                    log(TAG, WARN, "swapping children");
                    navigation.bringToFront();
                }
            }

            //scrollEnabled = false;
            final boolean expanded = navigation.getTranslationY() == 0;
            if (snackbarHeight == -1) {
                snackbarHeight = child.getHeight();
            }

            log(TAG, VERBOSE, "snack.height:%d, nav.height: %d, snack.y:%g, nav.y:%g, expanded: %b",
                    snackbarHeight,
                    height,
                    child.getTranslationY(),
                    navigation.getTranslationY(),
                    expanded
            );

            final float maxScroll = Math.max(0, navigation.getTranslationY() - bottomInset);
            layoutParams.bottomMargin = (int) (height - maxScroll);
            child.requestLayout();

            return true;
        }

        @Override
        void onDestroy() {
            log(TAG, INFO, "onDestroy");
            //scrollEnabled = true;
        }
    }

    public interface OnExpandStatusChangeListener {
        void onExpandStatusChanged(boolean expanded, final boolean animate);
    }

    public static void log(final String tag, final int level, String message, Object... arguments) {
        if (BottomNavigationView.DEBUG) {
            Log.println(level, tag, String.format(message, arguments));
        }
    }

}