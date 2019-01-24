package com.android.car.media.widgets;

import android.annotation.Nullable;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.transition.Fade;
import android.transition.Transition;
import android.transition.TransitionManager;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.android.car.media.R;
import com.android.car.media.common.MediaAppSelectorWidget;
import com.android.car.media.common.MediaItemMetadata;

import java.util.List;
import java.util.Objects;

/**
 * Media template application bar. A detailed explanation of all possible states of this
 * application bar can be seen at {@link AppBarView.State}.
 */
public class AppBarView extends RelativeLayout {
    private static final String TAG = "AppBarView";
    /** Default number of tabs to show on this app bar */
    private static int DEFAULT_MAX_TABS = 4;

    private LinearLayout mTabsContainer;
    private ImageView mNavIcon;
    private ViewGroup mNavIconContainer;
    private TextView mTitle;
    private View mSettingsButton;
    private View mSearchButton;
    private EditText mSearchText;
    private MediaAppSelectorWidget mAppSelector;
    private Context mContext;
    private int mMaxTabs;
    private Drawable mArrowBack;
    private Drawable mCollapse;
    private State mState = State.BROWSING;
    private AppBarListener mListener;
    private int mFadeDuration;
    private float mSelectedTabAlpha;
    private float mUnselectedTabAlpha;
    private MediaItemMetadata mSelectedItem;
    private String mMediaAppTitle;
    private boolean mContentForwardEnabled;
    private boolean mSearchSupported;
    private boolean mShowTabs = true;

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
         * Invoked when the user clicks on the settings button.
         */
        void onSettingsSelection();

        /**
         * Invoked when the user submits a search query.
         */
        void onSearch(String query);

        /**
         * Invoked when the user clicks on the search button
         */
        void onSearchSelection();
    }

    /**
     * Possible states of this application bar
     */
    public enum State {
        /**
         * Normal application state. If we are able to obtain media items from the media
         * source application, we display them as tabs. Otherwise we show the application name.
         */
        BROWSING,
        /**
         * Indicates that the user has navigated into an element. In this case we show
         * the name of the element and we disable the back button.
         */
        STACKED,
        /**
         * Indicates that we have expanded a view that can be collapsed. We show the
         * title of the application and a collapse icon
         */
        PLAYING,
        /**
         * Indicates that the user is currently entering a search query. We show the search bar and
         * a collapse icon
         */
        SEARCHING,
        /**
         * Used whenever the app bar should not display any information such as when MediaCenter
         * is in an error state
         */
        EMPTY
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
        mNavIconContainer = findViewById(R.id.nav_icon_container);
        mNavIconContainer.setOnClickListener(view -> onNavIconClicked());
        mAppSelector = findViewById(R.id.app_switch_container);
        mSettingsButton = findViewById(R.id.settings);
        mSettingsButton.setOnClickListener(view -> onSettingsClicked());
        mSearchButton = findViewById(R.id.search);
        mSearchButton.setOnClickListener(view -> onSearchClicked());
        mSearchText = findViewById(R.id.search_bar);
        mSearchText.setOnFocusChangeListener(
                (view, hasFocus) -> {
                    if (hasFocus) {
                        mSearchText.setCursorVisible(true);
                        ((InputMethodManager)
                                context.getSystemService(Context.INPUT_METHOD_SERVICE))
                                .showSoftInput(view, 0);
                    } else {
                        mSearchText.setCursorVisible(false);
                        ((InputMethodManager)
                                context.getSystemService(Context.INPUT_METHOD_SERVICE))
                                .hideSoftInputFromWindow(view.getWindowToken(), 0);
                    }
                });
        mSearchText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                onSearch(editable.toString());
            }
        });
        mSearchText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                mSearchText.setCursorVisible(false);
            }
            return false;
        });

        mTitle = findViewById(R.id.title);
        mArrowBack = getResources().getDrawable(R.drawable.ic_arrow_back, null);
        mCollapse = getResources().getDrawable(R.drawable.ic_expand_more, null);
        mFadeDuration = getResources().getInteger(R.integer.app_selector_fade_duration);
        TypedValue outValue = new TypedValue();
        getResources().getValue(R.dimen.browse_tab_alpha_selected, outValue, true);
        mSelectedTabAlpha = outValue.getFloat();
        getResources().getValue(R.dimen.browse_tab_alpha_unselected, outValue, true);
        mUnselectedTabAlpha = outValue.getFloat();
        mMediaAppTitle = getResources().getString(R.string.media_app_title);

        setState(State.BROWSING);
    }

    public void openAppSelector() {
        mAppSelector.open();
    }

    private void onNavIconClicked() {
        if (mListener == null) {
            return;
        }
        switch (mState) {
            case BROWSING:
            case STACKED:
                mListener.onBack();
                break;
            case SEARCHING:
                hideSearchBar();
            case PLAYING:
                mListener.onCollapse();
                break;
        }
    }

    private void onSettingsClicked() {
        if (mListener == null) {
            return;
        }
        mListener.onSettingsSelection();
    }

    private void onSearchClicked() {
        if (mListener == null) {
            return;
        }
        mListener.onSearchSelection();
    }

    private void onSearch(String query) {
        if (mListener == null || TextUtils.isEmpty(query)) {
            return;
        }
        mListener.onSearch(query);
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
     *
     * @param items list of tabs to show, or null if no tabs should be shown.
     */
    public void setItems(@Nullable List<MediaItemMetadata> items) {
        mTabsContainer.removeAllViews();

        if (items != null && !items.isEmpty()) {
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
                tab.setTag(item);

                count++;
                if (count >= mMaxTabs) {
                    break;
                }
            }
        }

        // Refresh the views visibility
        setState(mState);
    }

    /**
     * Updates the title to display when the bar is not showing tabs. If the provided title is null,
     * will default to displaying the app name.
     */
    public void setTitle(CharSequence title) {
        mTitle.setText(title != null ? title : mMediaAppTitle);
    }

    /**
     * Sets the name of the currently displayed media app. This is used as the default title for
     * playback and the root browse menu
     */
    public void setMediaAppName(CharSequence appName) {
        mMediaAppTitle = appName == null ? getResources().getString(R.string.media_app_title)
                : appName.toString();
    }

    /** Controls whether the settings button is visible. */
    public void showSettings(boolean show) {
        mSettingsButton.setVisibility(show ? VISIBLE : GONE);
    }

    /**
     * Whether content forward browsing is enabled or not
     */
    public void setContentForwardEnabled(boolean enabled) {
        mContentForwardEnabled = enabled;
    }

    /**
     * Updates the currently active item
     */
    public void setActiveItem(MediaItemMetadata item) {
        mSelectedItem = item;
        updateTabs();
    }

    /**
     * Sets whether the search box should be shown
     */
    public void setSearchSupported(boolean supported) {
        mSearchSupported = supported;
        mSearchButton.setVisibility(mSearchSupported ? View.VISIBLE : View.GONE);
    }

    /**
     * Sets whether the nav icon should be shown
     */
    public void setNavIconVisible(boolean visible) {
        mNavIconContainer.setVisibility(visible ? VISIBLE : INVISIBLE);
    }

    /**
     * Sets whether to show tabs or not. If there is no tab content, tabs will be hidden regardless
     * of this setting. By default, tabs will be shown if there is tab content.
     */
    public void setShowTabs(boolean visible) {
        mShowTabs = visible;
        // Refresh state to adjust for new tab visibility
        boolean showTabs = mTabsContainer.getChildCount() > 0 && mShowTabs;
        mTabsContainer.setVisibility(showTabs ? View.VISIBLE : View.GONE);
        mTitle.setVisibility(showTabs ? View.GONE : View.VISIBLE);
    }

    private void updateTabs() {
        for (int i = 0; i < mTabsContainer.getChildCount(); i++) {
            View child = mTabsContainer.getChildAt(i);
            if (child instanceof MediaItemTabView) {
                MediaItemTabView tabView = (MediaItemTabView) child;
                boolean match = mSelectedItem != null && Objects.equals(
                        mSelectedItem.getId(),
                        ((MediaItemMetadata) tabView.getTag()).getId());
                tabView.setAlpha(match ? mSelectedTabAlpha : mUnselectedTabAlpha);
            }
        }
    }

    /**
     * Updates the state of the bar.
     */
    public void setState(State state) {
        boolean showTabs = mTabsContainer.getChildCount() > 0 && mShowTabs;
        mState = state;

        Transition transition = new Fade().setDuration(mFadeDuration);
        TransitionManager.beginDelayedTransition(this, transition);
        Log.d(TAG, "Updating state: " + state + " (show tabs: " + showTabs + ")");
        switch (state) {
            case EMPTY:
                mNavIconContainer.setVisibility(View.GONE);
                mTabsContainer.setVisibility(View.GONE);
                mTitle.setVisibility(View.GONE);
                hideSearchBar();
            case BROWSING:
                mNavIconContainer.setVisibility(View.INVISIBLE);
                mNavIcon.setImageDrawable(mArrowBack);
                mTabsContainer.setVisibility(showTabs ? View.VISIBLE : View.GONE);
                mTitle.setVisibility(showTabs ? View.GONE : View.VISIBLE);
                hideSearchBar();
                mSearchButton.setVisibility(mSearchSupported ? View.VISIBLE : View.GONE);
                break;
            case STACKED:
                mNavIcon.setImageDrawable(mArrowBack);
                mNavIconContainer.setVisibility(View.VISIBLE);
                mTabsContainer.setVisibility(View.GONE);
                mTitle.setVisibility(View.VISIBLE);
                hideSearchBar();
                mSearchButton.setVisibility(View.GONE);
                break;
            case PLAYING:
                mNavIcon.setImageDrawable(mCollapse);
                mNavIconContainer.setVisibility(!mContentForwardEnabled ? View.GONE : View.VISIBLE);
                setActiveItem(null);
                mTabsContainer.setVisibility(showTabs && mContentForwardEnabled ? View.VISIBLE
                        : View.GONE);
                mTitle.setText(mMediaAppTitle);
                mTitle.setVisibility(showTabs || !mContentForwardEnabled ? View.GONE
                        : View.VISIBLE);
                hideSearchBar();
                mSearchButton.setVisibility(mSearchSupported ? View.VISIBLE : View.GONE);
                break;
            case SEARCHING:
                mNavIcon.setImageDrawable(mCollapse);
                mNavIconContainer.setVisibility(View.VISIBLE);
                mTabsContainer.setVisibility(View.GONE);
                mTitle.setVisibility(View.GONE);
                mSearchText.setVisibility(View.VISIBLE);
                mSearchText.requestFocus();
                break;
        }
    }

    private void hideSearchBar() {
        mSearchText.setVisibility(View.GONE);
        mSearchText.getText().clear();
    }
}
