package com.android.car.media;

import android.app.PendingIntent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.android.car.apps.common.CarUxRestrictionsUtil;

/**
 * A {@link Fragment} that displays the playback state error.
 */
public class ErrorFragment extends Fragment {
    private final String TAG = "ErrorFragment";

    private static final String ERROR_RESOLUTION_ACTION_MESSAGE = "ERROR_RESOLUTION_ACTION_MESSAGE";
    private static final String ERROR_RESOLUTION_ACTION_LABEL = "ERROR_RESOLUTION_ACTION_LABEL";
    private static final String ERROR_RESOLUTION_ACTION_INTENT = "ERROR_RESOLUTION_ACTION_INTENT";

    private TextView mErrorMessageView;
    private Button mErrorButton;

    private String mErrorMessageStr;
    private String mErrorLabel;
    private PendingIntent mPendingIntent;

    public static ErrorFragment newInstance(String message, String label, PendingIntent intent) {
        ErrorFragment fragment = new ErrorFragment();

        Bundle args = new Bundle();
        args.putString(ERROR_RESOLUTION_ACTION_MESSAGE, message);
        args.putString(ERROR_RESOLUTION_ACTION_LABEL, label);
        args.putParcelable(ERROR_RESOLUTION_ACTION_INTENT, intent);
        fragment.setArguments(args);

        return fragment;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, final ViewGroup container,
            Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_error, container, false);

        mErrorMessageView = view.findViewById(R.id.error_message);
        mErrorButton = view.findViewById(R.id.error_button);

        Bundle args = getArguments();
        if (args != null) {
            mErrorMessageStr = args.getString(ERROR_RESOLUTION_ACTION_MESSAGE);
            mErrorLabel = args.getString(ERROR_RESOLUTION_ACTION_LABEL);
            mPendingIntent = args.getParcelable(ERROR_RESOLUTION_ACTION_INTENT);
        }

        if (mErrorMessageStr == null) {
            Log.e(TAG, "ErrorFragment does not have an error message");
            return view;
        }

        CarUxRestrictionsUtil carUxRestrictionsUtil = CarUxRestrictionsUtil.getInstance(
                getActivity().getApplication());
        mErrorMessageStr = carUxRestrictionsUtil.restrictString(mErrorMessageStr);
        mErrorMessageView.setText(mErrorMessageStr);

        // Only an error message is required. Fragments without a provided message and label
        // have these elements omitted.
        if (mErrorLabel != null && mPendingIntent != null) {
            mErrorButton.setText(mErrorLabel);
            mErrorButton.setOnClickListener(v -> {
                try {
                    mPendingIntent.send();
                } catch (PendingIntent.CanceledException e) {
                    if (Log.isLoggable(TAG, Log.ERROR)) {
                        Log.e(TAG, "Pending intent canceled");
                    }
                }
            });
            mErrorButton.setVisibility(View.VISIBLE);
        } else {
            mErrorButton.setVisibility(View.GONE);
        }

        return view;
    }
}
