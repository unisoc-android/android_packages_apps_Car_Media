package com.android.car.media.storage;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.android.car.media.common.source.MediaSource;
import com.android.car.media.common.source.MediaSourcesLiveData;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.stream.Collectors;

/**
 * This class is used to manage the most recently used media sources. Uses SharedPreferences to
 * store the media sources, ordered by how recently they were used.
 */
public class MediaSourceStorage {

    private static final String LAST_MEDIA_SOURCE_SHARED_PREF_KEY = "last_media_source";
    private static final String SHARED_PREF = "com.android.car.media";
    private final static String PACKAGE_NAME_SEPARATOR = ",";

    private final SharedPreferences mSharedPreferences;
    private final MediaSourcesLiveData mMediaSources;

    public MediaSourceStorage(Context context) {
        mSharedPreferences =
                context.getSharedPreferences(SHARED_PREF, Context.MODE_PRIVATE);
        mMediaSources = MediaSourcesLiveData.getInstance(context);
    }

    public void setLastMediaSource(MediaSource mediaSource) {
        String serialized = mSharedPreferences.getString(
                LAST_MEDIA_SOURCE_SHARED_PREF_KEY, null);
        if (serialized == null) {
            mSharedPreferences.edit().putString(
                    LAST_MEDIA_SOURCE_SHARED_PREF_KEY, mediaSource.getPackageName()).apply();
        } else {
            Deque<String> packageNames = getPackageNameList(serialized);
            packageNames.remove(mediaSource.getPackageName());
            packageNames.addFirst(mediaSource.getPackageName());
            mSharedPreferences.edit()
                    .putString(LAST_MEDIA_SOURCE_SHARED_PREF_KEY,
                            serializePackageNameList(packageNames))
                    .apply();
        }
    }

    /**
     * Gets the last browsed media source, excluding any custom sources. Returns {@code null} if no
     * non-custom sources have been selected. Filters out sources that are not available.
     */
    @Nullable
    public MediaSource getLastMediaSource() {
        String serialized = mSharedPreferences.getString(LAST_MEDIA_SOURCE_SHARED_PREF_KEY, null);
        if (TextUtils.isEmpty(serialized)) {
            return null;
        }

        List<MediaSource> sources = mMediaSources.getList();
        for (String packageName : getPackageNameList(serialized)) {
            MediaSource source = validateSourcePackage(packageName, sources);
            if (source != null && !source.isCustom()) {
                return source;
            }
        }
        return null;
    }

    /** Returns null if the given package can't be found in the given list of sources. */
    @Nullable
    private MediaSource validateSourcePackage(String packageName, List<MediaSource> sources) {
        if (packageName == null) {
            return null;
        }
        for (MediaSource mediaSource : sources) {
            if (mediaSource.getPackageName().equals(packageName)) {
                return mediaSource;
            }
        }
        return null;
    }

    /**
     * Gets all browsed media sources, ordered by most to least recently used
     */
    @NonNull
    public Deque<String> getAllPackageNames() {
        String serialized = mSharedPreferences.getString(LAST_MEDIA_SOURCE_SHARED_PREF_KEY, null);
        if (TextUtils.isEmpty(serialized)) {
            return new ArrayDeque();
        }
        return getPackageNameList(serialized);
    }

    private String serializePackageNameList(@NonNull Deque<String> packageNames) {
        return packageNames.stream().collect(Collectors.joining(PACKAGE_NAME_SEPARATOR));
    }

    private Deque<String> getPackageNameList(@NonNull String serialized) {
        String[] packageNames = serialized.split(PACKAGE_NAME_SEPARATOR);
        return new ArrayDeque(Arrays.asList(packageNames));
    }
}
