package com.android.car.media.widgets;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.lifecycle.LifecycleOwner;

import com.android.car.media.MetadataController;
import com.android.car.media.R;
import com.android.car.media.common.playback.PlaybackViewModel;

/**
 * A view that can be used to display the metadata and playback progress of a media item.
 * This view can be styled using the "MetadataView" styleable attributes.
 */
public class MetadataView extends RelativeLayout {
    private static final String TAG = "MetadataView";

    private MetadataController mMetadataController;

    public MetadataView(Context context) {
        this(context, null);
    }

    public MetadataView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MetadataView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public MetadataView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        LayoutInflater.from(context).inflate(R.layout.metadata_compact, this, true);
    }

    /**
     * Registers the {@link PlaybackViewModel} this widget will use to follow playback state.
     *
     * @param model {@link PlaybackViewModel} to watch.
     * @param owner the LifecycleOwner defining the scope of this View
     */
    public void setModel(@NonNull PlaybackViewModel model, @NonNull LifecycleOwner owner) {
        if (mMetadataController != null) {
            Log.w(TAG, "Model set more than once. Ignoring subsequent call.");
            return;
        }
        TextView title = findViewById(R.id.title);
        TextView subtitle = findViewById(R.id.subtitle);
        SeekBar seekBar = findViewById(R.id.seek_bar);
        mMetadataController = new MetadataController(owner, model,null,
                title, subtitle, null, seekBar, null);
    }

}
