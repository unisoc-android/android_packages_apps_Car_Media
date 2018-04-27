package com.android.car.media.widgets;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.support.design.widget.TabLayout;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.android.car.media.R;
import com.android.car.media.common.MediaItemMetadata;
import com.android.car.media.common.MediaSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Media template application bar. A detailed explanation of all possible states of this
 * application bar can be seen at {@link AppBarView.State}.
 */
public class AppBarView extends RelativeLayout {
    /** Default number of tabs to show on this app bar */
    private static int DEFAULT_MAX_TABS = 4;

    private List<MediaSource> mMediaSources = new ArrayList<>();
    private LinearLayout mTabsContainer;
    private ImageView mAppIcon;
    private ImageView mAppSwitchIcon;
    private ImageView mNavIcon;
    private TextView mTitle;
    private ViewGroup mAppSwitchContainer;
    private Context mContext;
    private int mMaxTabs;
    private Drawable mArrowDropDown;
    private Drawable mArrowDropUp;
    private Drawable mArrowBack;
    private Drawable mCollapse;
    private State mState = State.IDLE;
    private AppBarListener mListener;

    /**
     * Application bar listener
     */
    public interface AppBarListener {
        /**
         * Invoked when the user selects an item from the tabs
         */
        void onTabSelected(MediaItemMetadata item);

        /**
         * Invoked when the user clicks on the back button
         */
        void onBack();

        /**
         * Invoked when the user clicks on the collapse button
         */
        void onCollapse();

        /**
         * Invoked when the user clicks on the app selection switch
         */
        void onAppSelection();
    }

    /**
     * Possible states of this application bar
     */
    public enum State {
        /**
         * Normal application state. If we are able to obtain media items from the media
         * source application, we display them as tabs. Otherwise we show the application name.
         */
        IDLE,
        /**
         * Indicates that the user has navigated into an element. In this case we show
         * the name of the element and we disable the back button.
         */
        STACKED,
        /**
         * Indicates that we have expanded a view that can be collapsed. We show the
         * title of the application and a collapse icon
         */
        EXPANDED,
        /**
         * Used to indicate that the user is inside the app selector. In this case we disable
         * navigation, we show the title of the application and we show the app switch icon
         * point up
         */
        APP_SELECTION
    }

    public AppBarView(Context context) {
        this(context, null);
    }

    public AppBarView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AppBarView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public AppBarView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context, attrs, defStyleAttr, defStyleRes);
    }

    private void init(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        TypedArray ta = context.obtainStyledAttributes(
                attrs, R.styleable.AppBarView, defStyleAttr, defStyleRes);
        mMaxTabs = ta.getInteger(R.styleable.AppBarView_max_tabs, DEFAULT_MAX_TABS);
        ta.recycle();

        LayoutInflater inflater = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.appbar_view, this, true);

        mContext = context;
        mTabsContainer = findViewById(R.id.tabs);
        mNavIcon = findViewById(R.id.nav_icon);
        mNavIcon.setOnClickListener(view -> onNavIconClicked());
        mAppIcon = findViewById(R.id.app_icon);
        mAppSwitchIcon = findViewById(R.id.app_switch_icon);
        mAppSwitchContainer = findViewById(R.id.app_switch_container);
        mAppSwitchContainer.setOnClickListener(view -> onAppSwitchClicked());
        mTitle = findViewById(R.id.title);
        mArrowDropDown = getResources().getDrawable(R.drawable.ic_arrow_drop_down, null);
        mArrowDropUp = getResources().getDrawable(R.drawable.ic_arrow_drop_up, null);
        mArrowBack = getResources().getDrawable(R.drawable.ic_arrow_back, null);
        mCollapse = getResources().getDrawable(R.drawable.ic_expand_more, null);

        setState(State.IDLE);
    }

    private void onNavIconClicked() {
        if (mListener == null) {
            return;
        }
        switch (mState) {
            case STACKED:
                mListener.onBack();
                break;
            case EXPANDED:
                mListener.onCollapse();
                break;
        }
    }

    private void onAppSwitchClicked() {
        if (mListener == null) {
            return;
        }
        mListener.onAppSelection();
    }

    /**
     * Sets a listener of this application bar events. In order to avoid memory leaks, consumers
     * must reset this reference by setting the listener to null.
     */
    public void setListener(AppBarListener listener) {
        mListener = listener;
    }

    /**
     * Updates the list of items to show in the application bar tabs.
     */
    public void setItems(List<MediaItemMetadata> items) {
        mTabsContainer.removeAllViews();

        if (items != null) {
            int count = 0;
            int padding = mContext.getResources().getDimensionPixelSize(R.dimen.car_padding_4);
            int tabWidth = mContext.getResources().getDimensionPixelSize(R.dimen.browse_tab_width) +
                    2 * padding;
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                    tabWidth, ViewGroup.LayoutParams.MATCH_PARENT);
            for (MediaItemMetadata item : items) {
                MediaItemTabView tab = new MediaItemTabView(mContext, item);
                mTabsContainer.addView(tab);
                tab.setLayoutParams(layoutParams);
                tab.setOnClickListener(view -> {
                    if (mListener != null) {
                        mListener.onTabSelected(item);
                    }
                });
                tab.setPadding(padding, 0, padding, 0);
                tab.requestLayout();
                count++;
                if (count >= mMaxTabs) {
                    break;
                }
            }
        }

        if (mState == State.IDLE) {
            boolean hasItems = items != null && !items.isEmpty();
            mTabsContainer.setVisibility(hasItems ? View.VISIBLE : View.GONE);
            mTitle.setVisibility(hasItems ? View.GONE : View.VISIBLE);
        }
    }

    /**
     * Updates the title to display when the bar is not showing tabs.
     */
    public void setTitle(CharSequence title) {
        mTitle.setText(title);
    }

    /**
     * Updates the application icon to show next to the application switcher.
     */
    public void setAppIcon(Bitmap icon) {
        mAppIcon.setImageBitmap(icon);
        mAppIcon.setVisibility(icon != null ? View.VISIBLE : View.GONE);
    }

    /**
     * Indicates whether or not the application switcher should be enabled.
     */
    public void setAppSelection(boolean enabled) {
        mAppSwitchIcon.setVisibility(enabled ? View.VISIBLE : View.GONE);
    }

    /**
     * Updates the state of the bar.
     */
    public void setState(State state) {
        boolean hasItems = mTabsContainer.getChildCount() > 0;
        mState = state;

        switch (state) {
            case IDLE:
                mNavIcon.setVisibility(View.GONE);
                mTabsContainer.setVisibility(hasItems ? View.VISIBLE : View.GONE);
                mTitle.setVisibility(hasItems ? View.GONE : View.VISIBLE);
                mAppSwitchIcon.setImageDrawable(mArrowDropDown);
                break;
            case STACKED:
                mNavIcon.setImageDrawable(mArrowBack);
                mNavIcon.setVisibility(View.VISIBLE);
                mTabsContainer.setVisibility(View.GONE);
                mTitle.setVisibility(View.VISIBLE);
                mAppSwitchIcon.setImageDrawable(mArrowDropDown);
                break;
            case EXPANDED:
                mNavIcon.setImageDrawable(mCollapse);
                mNavIcon.setVisibility(View.VISIBLE);
                mTabsContainer.setVisibility(View.GONE);
                mTitle.setVisibility(View.VISIBLE);
                mAppSwitchIcon.setImageDrawable(mArrowDropDown);
                break;
            case APP_SELECTION:
                mNavIcon.setVisibility(View.GONE);
                mTabsContainer.setVisibility(View.GONE);
                mTitle.setVisibility(View.VISIBLE);
                mAppSwitchIcon.setImageDrawable(mArrowDropUp);
                break;
        }
    }
}
