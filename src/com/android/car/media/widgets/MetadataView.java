package com.android.car.media.widgets;

import static java.lang.annotation.RetentionPolicy.SOURCE;

import android.annotation.IntDef;
import android.annotation.Nullable;
import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.android.car.media.R;
import com.android.car.media.common.MediaItemMetadata;
import com.android.car.media.common.PlaybackModel;

import java.lang.annotation.Retention;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

/**
 * A view that can be used to display the metadata and playback progress of a media item.
 * This view can be styled using the "MetadataView" styleable attributes.
 */
public class MetadataView extends RelativeLayout {
    private static final DateFormat TIME_FORMAT = new SimpleDateFormat("m:ss", Locale.US);

    @Nullable
    private MediaItemMetadata mCurrentMetadata;
    private TextView mTitle;
    private TextView mTime;
    private TextView mSubtitle;
    private SeekBar mSeekbar;
    @Nullable
    private PlaybackModel mModel;

    private PlaybackModel.PlaybackObserver mPlaybackObserver =
            new PlaybackModel.PlaybackObserver() {
        @Override
        public void onPlaybackStateChanged() {
            updateState();
        }

        @Override
        public void onSourceChanged() {
            updateState();
            updateMetadata();
        }

        @Override
        public void onMetadataChanged() {
            updateMetadata();
        }
    };

    /**
     * The possible styles of this widget.
     */
    @IntDef({
            MetadataView.Style.COMPACT,
            MetadataView.Style.NORMAL
    })
    @Retention(SOURCE)
    public @interface Style {
        /** Compact style (small space between elements, no time progress indicator) */
        int COMPACT = 0;
        /** Normal style (normal spacing and progress indicator) */
        int NORMAL = 1;
    }

    public MetadataView(Context context) {
        super(context);
        init(context, null, 0, 0);
    }

    public MetadataView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs, 0, 0);
    }

    public MetadataView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs, defStyleAttr, 0);
    }

    public MetadataView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context, attrs, defStyleAttr, defStyleRes);
    }

    private void init(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        TypedArray ta = context.obtainStyledAttributes(
                attrs, R.styleable.MetadataView, defStyleAttr, defStyleRes);
        @Style int style = ta.getInteger(R.styleable.MetadataView_style, Style.NORMAL);
        ta.recycle();

        int layoutId = style == Style.COMPACT
                ? R.layout.metadata_compact
                : R.layout.metadata_normal;

        LayoutInflater inflater = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(layoutId, this, true);

        mTitle = findViewById(R.id.title);
        mSubtitle = findViewById(R.id.subtitle);
        mSeekbar = findViewById(R.id.seek_bar);
        mTime = findViewById(R.id.time);
    }

    /**
     * Registers the {@link PlaybackModel} this widget will use to follow playback state.
     * Consumers of this class must unregister the {@link PlaybackModel} by calling this method with
     * null.
     *
     * @param model {@link PlaybackModel} to subscribe, or null to unsubscribe.
     */
    public void setModel(@Nullable PlaybackModel model) {
        if (mModel != null) {
            mModel.unregisterObserver(mPlaybackObserver);
        }
        mModel = model;
        if (mModel != null) {
            mModel.registerObserver(mPlaybackObserver);
        }
    }

    private void updateState() {
        updateProgress();

        if (mModel != null && mModel.isPlaying()) {
            mSeekbar.post(mSeekBarRunnable);
        } else {
            mSeekbar.removeCallbacks(mSeekBarRunnable);
        }
    }

    private void updateMetadata() {
        MediaItemMetadata metadata = mModel != null ? mModel.getMetadata() : null;
        if (Objects.equals(mCurrentMetadata, metadata)) {
            return;
        }
        mCurrentMetadata = metadata;
        mTitle.setText(metadata != null ? metadata.getTitle() : null);
        mSubtitle.setText(metadata != null ? metadata.getSubtitle() : null);
    }

    private static final long SEEK_BAR_UPDATE_TIME_INTERVAL_MS = 1000;

    private final Runnable mSeekBarRunnable = new Runnable() {
        @Override
        public void run() {
            if (mModel == null || !mModel.isPlaying()) {
                return;
            }
            updateProgress();
            mSeekbar.postDelayed(this, SEEK_BAR_UPDATE_TIME_INTERVAL_MS);

        }
    };

    private void updateProgress() {
        if (mModel == null) {
            mTime.setVisibility(View.INVISIBLE);
            mSeekbar.setVisibility(View.INVISIBLE);
            return;
        }
        long maxProgress = mModel.getMaxProgress();
        int visibility = maxProgress > 0 ? View.VISIBLE : View.INVISIBLE;
        if (mTime != null) {
            String time = String.format("%s / %s",
                    TIME_FORMAT.format(new Date(mModel.getProgress())),
                    TIME_FORMAT.format(new Date(maxProgress)));
            mTime.setVisibility(visibility);
            mTime.setText(time);
        }
        mSeekbar.setVisibility(visibility);
        mSeekbar.setMax((int) mModel.getMaxProgress());
        mSeekbar.setProgress((int) mModel.getProgress());
    }
}
