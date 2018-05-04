package com.android.car.media.widgets;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.NonNull;
import android.view.View;

/**
 * Utility methods to operate over views.
 */
public class ViewUtils {
    /**
     * Hides a view using a fade-out animation
     *
     * @param view {@link View} to be hidden
     * @param duration animation duration in milliseconds.
     */
    public static void hideViewAnimated(@NonNull View view, int duration) {
        if (view.getVisibility() == View.GONE) {
            return;
        }
        view.animate()
                .alpha(0f)
                .setDuration(duration)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        view.setVisibility(View.GONE);
                    }
                });
    }

    /**
     * Shows a view using a fade-in animation
     *
     * @param view {@link View} to be shown
     * @param duration animation duration in milliseconds.
     */
    public static void showViewAnimated(@NonNull View view, int duration) {
        if (view.getVisibility() == View.VISIBLE) {
            return;
        }
        view.setAlpha(0f);
        view.setVisibility(View.VISIBLE);
        view.animate()
                .alpha(1f)
                .setDuration(duration)
                .setListener(null);
    }
}
