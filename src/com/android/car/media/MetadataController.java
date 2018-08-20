package com.android.car.media;

import static com.android.car.arch.common.LiveDataFunctions.pair;
import static com.android.car.arch.common.LiveDataFunctions.split;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.media.session.PlaybackState;
import android.view.View;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.lifecycle.LifecycleOwner;

import com.android.car.media.common.MediaItemMetadata;
import com.android.car.media.common.playback.PlaybackViewModel;

import java.util.concurrent.TimeUnit;

/**
 * Common controller for displaying current track's metadata.
 */
public class MetadataController {

    @NonNull
    private final TextView mTitle;
    @NonNull
    private final TextView mSubtitle;
    @Nullable
    private final TextView mTime;
    @NonNull
    private final SeekBar mSeekBar;
    @Nullable
    private final ImageView mAlbumArt;

    @NonNull
    private final PlaybackViewModel mModel;

    private boolean mUpdatesPaused;
    private boolean mNeedsMetadataUpdate;
    private int mAlbumArtSize;

    /**
     * Create a new MetadataController that operates on the provided Views
     *
     * @param lifecycleOwner The lifecycle scope for the Views provided to this controller
     * @param viewModel      The ViewModel to provide metadata for display
     * @param title          Displays the track's title. Must not be {@code null}.
     * @param subtitle       Displays the track's artist. Must not be {@code null}.
     * @param time           Displays the track's progress as text. May be {@code null}.
     * @param seekBar        Displays the track's progress visually. Must not be {@code null}.
     * @param albumArt       Displays the track's album art. May be {@code null}.
     */
    public MetadataController(@NonNull LifecycleOwner lifecycleOwner,
            @NonNull PlaybackViewModel viewModel, @NonNull TextView title,
            @NonNull TextView subtitle, @Nullable TextView time, @NonNull SeekBar seekBar,
            @Nullable ImageView albumArt) {
        mModel = viewModel;
        mTitle = title;
        mSubtitle = subtitle;
        mTime = time;
        mSeekBar = seekBar;
        mSeekBar.setOnTouchListener((view, event) -> true);
        mAlbumArt = albumArt;
        mAlbumArtSize = title.getContext().getResources()
                .getDimensionPixelSize(R.dimen.playback_album_art_size_large);

        viewModel.getMetadata().observe(lifecycleOwner, this::updateMetadata);
        PlaybackViewModel.PlaybackInfo playbackInfo = viewModel.getPlaybackInfo();
        pair(playbackInfo.getProgress(), playbackInfo.getMaxProgress()).observe(lifecycleOwner,
                split((progress, maxProgress) -> {
                    int visibility =
                            maxProgress > 0 && progress != PlaybackState.PLAYBACK_POSITION_UNKNOWN
                                    ? View.VISIBLE : View.INVISIBLE;
                    if (mTime != null) {
                        boolean showHours = TimeUnit.MILLISECONDS.toHours(maxProgress) > 0;
                        String formattedTime = String.format("%s / %s",
                                formatTime(progress, showHours),
                                formatTime(maxProgress, showHours));
                        mTime.setVisibility(visibility);
                        mTime.setText(formattedTime);
                    }
                    mSeekBar.setVisibility(visibility);
                    mSeekBar.setMax(maxProgress.intValue());
                    mSeekBar.setProgress(progress.intValue());
                }));
    }

    private void updateMetadata(MediaItemMetadata metadata) {
        if (mUpdatesPaused) {
            mNeedsMetadataUpdate = true;
            return;
        }

        mNeedsMetadataUpdate = false;
        mTitle.setText(metadata != null ? metadata.getTitle() : null);
        mSubtitle.setText(metadata != null ? metadata.getSubtitle() : null);
        if (mAlbumArt != null && metadata != null && (metadata.getAlbumArtUri() != null
                || metadata.getAlbumArtBitmap() != null)) {
            mAlbumArt.setVisibility(View.VISIBLE);
            metadata.getAlbumArt(mAlbumArt.getContext(), mAlbumArtSize, mAlbumArtSize, true)
                    .thenAccept(mAlbumArt::setImageBitmap);
        } else if (mAlbumArt != null) {
            mAlbumArt.setVisibility(View.GONE);
        }
    }

    public void pauseUpdates() {
        mUpdatesPaused = true;
    }

    public void resumeUpdates() {
        mUpdatesPaused = false;
        if (mNeedsMetadataUpdate) {
            updateMetadata(mModel.getMetadata().getValue());
        }
    }

    @SuppressLint("DefaultLocale")
    private static String formatTime(long millis, boolean showHours) {
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % TimeUnit.HOURS.toMinutes(1);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % TimeUnit.MINUTES.toSeconds(1);
        if (showHours) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%d:%02d", minutes, seconds);
        }
    }
}
