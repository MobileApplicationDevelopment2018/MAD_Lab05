package it.polito.mad.mad2018.explore;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.algolia.instantsearch.helpers.Searcher;
import com.algolia.instantsearch.model.NumericRefinement;
import com.algolia.search.saas.AbstractQuery;
import com.algolia.search.saas.Query;

import java.util.List;

import it.polito.mad.mad2018.MAD2018Application;
import it.polito.mad.mad2018.R;
import it.polito.mad.mad2018.data.Book;
import it.polito.mad.mad2018.data.LocalUserProfile;

public class FilterResultsFragment extends DialogFragment {

    public static final String TAG = "FilterResultsFragment";

    static final String CONDITIONS_FILTER_NAME = "conditions";
    static final String DISTANCE_FILTER_NAME = "distance";
    static final String AVAILABILITY_FILTER_NAME = "availability";

    private Searcher searcher;

    private ConditionsFilter conditionsFilter;
    private DistanceFilter distanceFilter;
    private CheckBoxFilter availabilityFilter;
    private Object[] filterValues = new Object[3];

    public static FilterResultsFragment getInstance(Searcher searcher) {
        final FilterResultsFragment fragment = new FilterResultsFragment();
        fragment.searcher = searcher;
        Bundle args = new Bundle();
        fragment.setArguments(args);
        fragment.createFilters();
        return fragment;
    }

    private void createFilters() {
        final List<Book.BookConditions> bc = Book.BookConditions.values();
        conditionsFilter = new ConditionsFilter(CONDITIONS_FILTER_NAME,
                bc.get(0).value, bc.get(bc.size() - 1).value, bc.size() - 1, 0);
        conditionsFilter.value = conditionsFilter.min;
        conditionsFilter.applyFilter();
        distanceFilter = new DistanceFilter(DISTANCE_FILTER_NAME, 0, 1000000, 50, 1);
        distanceFilter.value = distanceFilter.max;
        distanceFilter.applyFilter();
        availabilityFilter = new CheckBoxFilter(AVAILABILITY_FILTER_NAME, true, MAD2018Application.getApplicationContextStatic().getString(R.string.filter_available_books), 2);
        availabilityFilter.value = false;
        availabilityFilter.applyFilter();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        setRetainInstance(true);
        super.onCreate(savedInstanceState);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreateDialog(savedInstanceState);

        searcher = Searcher.get();
        checkHasSearcher();

        final FragmentActivity activity = getActivity();
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);

        conditionsFilter.createView();
        distanceFilter.createView();
        availabilityFilter.createView();

        layout.addView(conditionsFilter.filterLayout);
        layout.addView(distanceFilter.filterLayout);
        layout.addView(availabilityFilter.filterLayout);

        saveFiltersValues();

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        ScrollView scrollView = new ScrollView(activity);
        scrollView.addView(layout);
        builder.setTitle(getString(R.string.filter_results)).setView(scrollView)
                .setPositiveButton(getString(R.string.search), (dialog, which) -> {
                    conditionsFilter.applyFilter();
                    distanceFilter.applyFilter();
                    availabilityFilter.applyFilter();
                    searcher.search();
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> {
                    restoreFiltersValues();
                    dialog.dismiss();
                });
        return builder.create();
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        super.onCancel(dialog);
        restoreFiltersValues();
    }

    private void restoreFiltersValues() {
        conditionsFilter.value = (int) filterValues[0];
        distanceFilter.value = (int) filterValues[1];
        availabilityFilter.value = (boolean) filterValues[2];
    }

    private void saveFiltersValues() {
        filterValues[0] = conditionsFilter.value;
        filterValues[1] = distanceFilter.value;
        filterValues[2] = availabilityFilter.value;
    }

    private void checkHasSearcher() {
        if (searcher == null) {
            throw new IllegalStateException("No searcher found");
        }
    }

    private abstract class Filter {

        final String attribute;
        final String name;
        final int position;
        int value;

        Filter(@Nullable String attribute, String name, int position) {
            this.attribute = attribute;
            this.name = name;
            this.position = position;
            if (attribute != null) {
                searcher.addFacet(attribute);
            }
        }

        void applyFilter() {
            checkHasSearcher();
        }

        View getInflatedLayout(int layoutId) {
            Activity activity = getActivity();
            LayoutInflater inflater = activity.getLayoutInflater();
            return inflater.inflate(layoutId, null);
        }
    }

    private abstract class ViewFilter extends Filter {
        View filterLayout;

        ViewFilter(String attribute, String name, int position) {
            super(attribute, name, position);
        }

        abstract void createView();
    }

    private abstract class SeekBarFilter extends ViewFilter {
        int min;
        int max;
        int steps;
        int seekBarValue;

        SeekBarFilter(String attribute, String name, int min, int max, int steps, int position) {
            super(attribute, name, position);
            this.min = min;
            this.max = max;
            this.steps = steps;
        }

        @Override
        void createView() {
            filterLayout = getInflatedLayout(R.layout.layout_seekbar);

            final TextView textView = filterLayout.findViewById(R.id.dialog_seekbar_text);
            final SeekBar seekBar = filterLayout.findViewById(R.id.dialog_seekbar_bar);
            seekBar.setMax(steps);
            seekBarValue = getSeekBarValue(value);
            seekBar.setProgress(seekBarValue);
            updateSeekBarText(textView);

            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    onUpdate(seekBar);
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    onUpdate(seekBar);
                }

                private void onUpdate(final SeekBar seekBar) {
                    seekBarValue = seekBar.getProgress();
                    value = getActualValue(seekBarValue);
                    updateSeekBarText(textView);
                }
            });
        }

        private int getActualValue(int seekBarValue) {
            return min + seekBarValue * (max - min) / steps;
        }

        private int getSeekBarValue(int filterValue) {
            return (filterValue - min) * steps / (max - min);
        }

        abstract void updateSeekBarText(final TextView textView);
    }

    private class ConditionsFilter extends SeekBarFilter {

        ConditionsFilter(String name, int min, int max, int steps, int position) {
            super(Book.ALGOLIA_CONDITIONS_KEY, name, min, max, steps, position);
        }

        @Override
        void applyFilter() {
            super.applyFilter();
            searcher.addNumericRefinement(new NumericRefinement(attribute, NumericRefinement.OPERATOR_GE, value));
        }

        @Override
        void updateSeekBarText(final TextView textView) {
            String text;
            if (value == min) {
                text = getString(R.string.book_condition_any);
            } else {
                text = getString(R.string.conditions_filter, getResources().getString(Book.BookConditions.getStringId(value)).toLowerCase());
            }
            textView.setText(text);
        }
    }

    private class DistanceFilter extends SeekBarFilter {

        DistanceFilter(String name, int min, int max, int steps, int position) {
            super(null, name, min, max, steps, position);
        }

        @Override
        void applyFilter() {
            super.applyFilter();
            double[] position = LocalUserProfile.getInstance().getCoordinates();
            Query query = searcher.getQuery().setAroundLatLng(new AbstractQuery.LatLng(position[0], position[1]));
            if (value < max) {
                query.setAroundRadius(value);
            } else {
                query.setAroundRadius(Query.RADIUS_ALL);
            }
        }

        @Override
        void updateSeekBarText(final TextView textView) {
            String text;
            if (value == max) {
                text = getString(R.string.no_distance_filter);
            } else {
                text = getString(R.string.maximum_distance, (int) value / 1000);
            }
            textView.setText(text);
        }
    }

    private class CheckBoxFilter extends ViewFilter {
        final boolean checkedIsTrue;
        final String text;
        boolean value;

        CheckBoxFilter(String name, boolean checkedIsTrue, String text, int position) {
            super(Book.ALGOLIA_AVAILABLE_KEY, name, position);
            this.checkedIsTrue = checkedIsTrue;
            this.text = text;
        }

        @Override
        void applyFilter() {
            super.applyFilter();

            if (attribute != null) {
                if (value) {
                    searcher.addBooleanFilter(attribute, checkedIsTrue);
                } else {
                    searcher.removeBooleanFilter(attribute);
                }
            }
        }

        @Override
        void createView() {
            filterLayout = getInflatedLayout(R.layout.layout_checkbox);

            final TextView tv = filterLayout.findViewById(R.id.dialog_checkbox_text);
            final CheckBox checkBox = filterLayout.findViewById(R.id.dialog_checkbox_box);

            tv.setText(text);
            checkBox.setChecked(value);

            checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> value = isChecked);
        }
    }
}