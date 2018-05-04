package com.android.car.media;

import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import com.android.car.media.widgets.ViewUtils;

/**
 * Empty fragment to show while we are loading content
 */
public class EmptyFragment extends Fragment {
    private ProgressBar mProgressBar;
    private int mProgressBarDelay;
    private Handler mHandler = new Handler();
    private int mFadeDuration;
    private Runnable mProgressIndicatorRunnable = new Runnable() {
        @Override
        public void run() {
            ViewUtils.showViewAnimated(mProgressBar, mFadeDuration);
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, final ViewGroup container,
            Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_empty, container, false);
        mProgressBar = view.findViewById(R.id.loading_spinner);
        mProgressBarDelay = getContext().getResources()
                .getInteger(R.integer.progress_indicator_delay);
        mFadeDuration = getContext().getResources().getInteger(
                R.integer.new_album_art_fade_in_duration);
        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        mProgressBar.setVisibility(View.GONE);
    }

    @Override
    public void onResume() {
        super.onResume();
        // Display the indicator after a certain time, to avoid flashing the indicator constantly,
        // even when performance is acceptable.
        mHandler.postDelayed(mProgressIndicatorRunnable, mProgressBarDelay);
    }

    @Override
    public void onPause() {
        super.onPause();
        mHandler.removeCallbacks(mProgressIndicatorRunnable);
        mProgressBar.setVisibility(View.GONE);
    }
}
