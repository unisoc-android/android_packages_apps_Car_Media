package com.android.car.media;

import android.annotation.NonNull;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.car.widget.PagedListView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.car.apps.common.FragmentUtils;
import com.android.car.media.common.source.MediaSource;
import com.android.car.media.common.source.MediaSourcesLiveData;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link Fragment} that implements the app selection UI
 */
public class AppSelectionFragment extends Fragment {

    private class AppGridAdapter extends RecyclerView.Adapter<AppItemViewHolder> {
        private List<MediaSource> mMediaSources;

        /**
         * Triggers a refresh of media sources
         */
        void updateSources(List<MediaSource> mediaSources) {
            mMediaSources = new ArrayList<>(mediaSources);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public AppItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.app_selection_item, parent, false);
            return new AppItemViewHolder(view);

        }

        @Override
        public void onBindViewHolder(@NonNull AppItemViewHolder vh, int position) {
            vh.bind(mMediaSources.get(position));
        }

        @Override
        public int getItemCount() {
            return mMediaSources.size();
        }
    }

    private class AppItemViewHolder extends RecyclerView.ViewHolder {
        View mAppItem;
        ImageView mAppIconView;
        TextView mAppNameView;

        AppItemViewHolder(View view) {
            super(view);
            mAppItem = view.findViewById(R.id.app_item);
            mAppIconView = mAppItem.findViewById(R.id.app_icon);
            mAppNameView = mAppItem.findViewById(R.id.app_name);
        }

        /**
         * Binds a media source to a view
         */
        void bind(@NonNull MediaSource mediaSource) {
            mAppItem.setOnClickListener(
                    v -> FragmentUtils
                            .requireParent(AppSelectionFragment.this, Callbacks.class)
                            .onMediaSourceSelected(mediaSource));
            mAppIconView.setImageDrawable(mediaSource.getPackageIcon());
            mAppNameView.setText(mediaSource.getName());
        }
    }

    /**
     * Fragment callbacks (implemented by this Fragment's parent)
     */
    public interface Callbacks {
        /**
         * Invoked when the user makes a selection
         */
        void onMediaSourceSelected(@NonNull MediaSource mediaSource);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, final ViewGroup container,
            Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_app_selection, container, false);
        int columnNumber = getResources().getInteger(R.integer.num_app_selector_columns);
        AppGridAdapter gridAdapter = new AppGridAdapter();
        PagedListView gridView = view.findViewById(R.id.apps_grid);

        GridLayoutManager gridLayoutManager = new GridLayoutManager(getContext(), columnNumber);
        gridView.getRecyclerView().setLayoutManager(gridLayoutManager);
        gridView.setAdapter(gridAdapter);

        gridAdapter.updateSources(MediaSourcesLiveData.getInstance(getContext()).getList());
        return view;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        FragmentUtils.checkParent(this, Callbacks.class);
    }

}
