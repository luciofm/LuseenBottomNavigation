package com.luseen.luseenbottomnavigation.BottomNavigation.behavior;

import android.support.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.SOURCE)
@IntDef({ScrollDirection.SCROLL_DIRECTION_UP, ScrollDirection.SCROLL_DIRECTION_DOWN})
public @interface ScrollDirection {
    int SCROLL_DIRECTION_UP = 1;
    int SCROLL_DIRECTION_DOWN = -1;
    int SCROLL_NONE = 0;
}