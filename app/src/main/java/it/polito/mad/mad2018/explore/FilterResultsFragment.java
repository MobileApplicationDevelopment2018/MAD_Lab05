package it.polito.mad.mad2018.explore;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import java.util.ArrayList;

import it.polito.mad.mad2018.R;

public class FilterResultsFragment extends DialogFragment {

    public static final String TAG = "FilterResultsFragment";

    private static final String FILTERS_KEY = "filters";

    private ArrayList<ExploreFragment.Filter> filters;

    public static FilterResultsFragment newInstance(ArrayList<ExploreFragment.Filter> filters) {
        final FilterResultsFragment fragment = new FilterResultsFragment();
        Bundle args = new Bundle();
        args.putParcelableArrayList(FILTERS_KEY, filters);
        fragment.setArguments(args);
        return fragment;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreateDialog(savedInstanceState);
        assert getArguments() != null;
        filters = getArguments().getParcelableArrayList(FILTERS_KEY);

        final FragmentActivity activity = getActivity();
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);

        for (ExploreFragment.Filter filter : filters) {

            Integer value = null;
            if (savedInstanceState != null) {
                value = savedInstanceState.getInt(filter.name);
            }
            filter.createView(value, activity);
            layout.addView(filter.filterLayout);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        ScrollView scrollView = new ScrollView(activity);
        scrollView.addView(layout);
        builder.setTitle(getString(R.string.filter_results)).setView(scrollView)
                .setPositiveButton(getString(R.string.search), (dialog, which) -> {
                    for (ExploreFragment.Filter filter : filters) {
                        filter.updateValue();
                        filter.applyFilter();
                    }

                    assert getParentFragment() != null;
                    ((OnDismissListener) getParentFragment()).onDialogDismiss();
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss());

        return builder.create();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        for (ExploreFragment.Filter filter : filters) {
            outState.putInt(filter.name, filter.getFilterLayoutValue());
        }
    }

    interface OnDismissListener {
        void onDialogDismiss();
    }
}