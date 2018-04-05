package com.android.car.media;

import static java.security.AccessController.getContext;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.widget.RecyclerView;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.android.car.apps.common.ImageUtils;
import com.android.car.media.common.MediaItemMetadata;
import com.android.car.media.common.PlaybackControls;
import com.android.car.media.common.PlaybackModel;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import androidx.car.widget.ListItem;
import androidx.car.widget.ListItemAdapter;
import androidx.car.widget.ListItemProvider;
import androidx.car.widget.PagedListView;
import androidx.car.widget.TextListItem;

/**
 * A {@link Fragment} that implements both the playback and the content forward browsing experience.
 * It observes a {@link PlaybackModel} and updates its information depending on the currently
 * playing media source through the {@link MediaSession} API.
 */
public class PlaybackFragment extends Fragment {
    private static final String TAG = "PlaybackFragment";
    private static final DateFormat TIME_FORMAT = new SimpleDateFormat("m:ss", Locale.US);

    private PlaybackModel mModel;
    private CrossfadeImageView mAlbumBackground;
    private PlaybackControls mPlaybackControls;
    private ImageView mAlbumArt;
    private TextView mTitle;
    private TextView mTime;
    private TextView mSubtitle;
    private SeekBar mSeekbar;
    private PagedListView mBrowseList;
    private ListItemAdapter mMediaAdapter;
    private int mBackgroundRawImageSize;
    private float mBackgroundBlurRadius;
    private float mBackgroundBlurScale;

    private PlaybackModel.PlaybackObserver mObserver = new PlaybackModel.PlaybackObserver() {
        @Override
        public void onPlaybackStateChanged() {
            updateState();
        }

        @Override
        public void onSourceChanged() {
            updateState();
            updateMetadata();
            updateAccentColor();
            updateBrowse();
        }

        @Override
        public void onMetadataChanged() {
            updateMetadata();
        }
    };

    private ListItemProvider mMediaItemsProvider = new ListItemProvider() {
        @Override
        public ListItem get(int position) {
            if (mModel == null || !mModel.hasQueue()) {
                return null;
            }
            List<MediaItemMetadata> queue = mModel.getQueue();
            if (position < 0 || position >= queue.size()) {
                return null;
            }
            MediaItemMetadata item = queue.get(position);
            TextListItem textListItem = new TextListItem(getContext());
            textListItem.setTitle(item.getTitle().toString());
            textListItem.setBody(item.getSubtitle().toString());
            textListItem.setOnClickListener(v -> mModel.onSkipToQueueItem(item.getQueueId()));
            return textListItem;
        }

        @Override
        public int size() {
            if (mModel == null || !mModel.hasQueue()) {
                return 0;
            }
            return mModel.getQueue().size();
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, final ViewGroup container,
            Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_playback, container, false);
        mModel = new PlaybackModel(getContext());
        mModel.registerObserver(mObserver);
        mAlbumBackground = view.findViewById(R.id.album_background);
        mPlaybackControls = view.findViewById(R.id.playback_controls);
        mPlaybackControls.setModel(mModel);
        ViewGroup playbackContainer = view.findViewById(R.id.playback_container);
        mPlaybackControls.setAnimationViewGroup(playbackContainer);
        mAlbumArt = view.findViewById(R.id.album_art);
        mTitle = view.findViewById(R.id.title);
        mSubtitle = view.findViewById(R.id.subtitle);
        mSeekbar = view.findViewById(R.id.seek_bar);
        mTime = view.findViewById(R.id.time);
        mBrowseList = view.findViewById(R.id.browse_list);
        mMediaAdapter = new ListItemAdapter(getContext(), mMediaItemsProvider);
        mBrowseList.setAdapter(mMediaAdapter);
        RecyclerView recyclerView = mBrowseList.findViewById(R.id.recycler_view);
        recyclerView.setVerticalFadingEdgeEnabled(true);
        recyclerView.setFadingEdgeLength(getResources()
                .getDimensionPixelSize(R.dimen.car_padding_3));
        TypedValue outValue = new TypedValue();
        getResources().getValue(R.dimen.playback_background_blur_radius, outValue, true);
        mBackgroundBlurRadius = outValue.getFloat();
        getResources().getValue(R.dimen.playback_background_blur_scale, outValue, true);
        mBackgroundBlurScale = outValue.getFloat();
        mBackgroundRawImageSize = getContext().getResources().getDimensionPixelSize(
                R.dimen.playback_background_raw_image_size);
        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        mModel.start();
        mMediaAdapter.start();
    }

    @Override
    public void onStop() {
        super.onStop();
        mModel.stop();
        mMediaAdapter.stop();
    }

    private void updateState() {
        updateProgress();

        if (mModel.isPlaying()) {
            mSeekbar.post(mSeekBarRunnable);
        } else {
            mSeekbar.removeCallbacks(mSeekBarRunnable);
        }
        mBrowseList.setVisibility(mModel.hasQueue() ? View.VISIBLE : View.GONE);
    }

    private void updateMetadata() {
        MediaItemMetadata metadata = mModel.getMetadata();
        mTitle.setText(metadata != null ? metadata.getTitle() : null);
        mSubtitle.setText(metadata != null ? metadata.getSubtitle() : null);
        MediaItemMetadata.updateImageView(getContext(), metadata, mAlbumArt, 0);
        if (metadata != null) {
            metadata.getAlbumArt(getContext(),
                    mBackgroundRawImageSize,
                    mBackgroundRawImageSize,
                    false)
                    .thenAccept(this::setBackgroundImage);
        } else {
            mAlbumBackground.setImageBitmap(null, true);
        }
    }

    private void setBackgroundImage(Bitmap bitmap) {
        // TODO(b/77551865): Implement image blurring once the following issue is solved:
        // b/77551557
        // bitmap = ImageUtils.blur(getContext(), bitmap, mBackgroundBlurScale,
        //        mBackgroundBlurRadius);
        mAlbumBackground.setImageBitmap(bitmap, true);
    }

    private void updateAccentColor() {
        int color = mModel.getAccentColor();
        mSeekbar.getProgressDrawable().setColorFilter(color, PorterDuff.Mode.SRC_IN);
    }

    private static final long SEEK_BAR_UPDATE_TIME_INTERVAL_MS = 500;

    private final Runnable mSeekBarRunnable = new Runnable() {
        @Override
        public void run() {
            if (!mModel.isPlaying()) {
                return;
            }
            updateProgress();
            mSeekbar.postDelayed(this, SEEK_BAR_UPDATE_TIME_INTERVAL_MS);

        }
    };

    private void updateProgress() {
        long maxProgress = mModel.getMaxProgress();
        int visibility = maxProgress > 0 ? View.VISIBLE : View.INVISIBLE;
        String time = String.format("%s / %s",
                TIME_FORMAT.format(new Date(mModel.getProgress())),
                TIME_FORMAT.format(new Date(maxProgress)));
        mTime.setVisibility(visibility);
        mTime.setText(time);
        mSeekbar.setVisibility(visibility);
        mSeekbar.setMax((int) mModel.getMaxProgress());
        mSeekbar.setProgress((int) mModel.getProgress());
    }

    /**
     * Collapses the playback controls.
     */
    public void closeOverflowMenu() {
        mPlaybackControls.close();
    }

    private void updateBrowse() {
        MediaManager.getInstance(getContext())
                .setMediaClientComponent(mModel.getMediaBrowseServiceComponent());
    }
}
