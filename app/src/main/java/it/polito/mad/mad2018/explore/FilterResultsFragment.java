package it.polito.mad.mad2018.explore;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.algolia.instantsearch.helpers.Searcher;
import com.algolia.instantsearch.model.FacetStat;
import com.algolia.instantsearch.model.NumericRefinement;
import com.algolia.search.saas.AbstractQuery;
import com.algolia.search.saas.Query;

import java.util.ArrayList;
import java.util.List;

import it.polito.mad.mad2018.R;
import it.polito.mad.mad2018.data.Book;
import it.polito.mad.mad2018.data.LocalUserProfile;

public class FilterResultsFragment extends DialogFragment {
    public static final String TAG = "FilterResultsFragment";

    static final String CONDITIONS_NAME = "conditions";
    static final String DISTANCE_NAME = "distance";

    private Searcher searcher;

    private List<FilterDescription> futureFilters = new ArrayList<>();
    private SparseArray<View> filterViews = new SparseArray<>();
    private int filterCount = 0;

    /**
     * Get a FilterResultsFragment instance linked with a given searcher.
     *
     * @param searcher the searcher object to associate with this fragment.
     * @return a fragment ready to use.
     */
    public static FilterResultsFragment getInstance(Searcher searcher) {
        final FilterResultsFragment fragment = new FilterResultsFragment();
        fragment.searcher = searcher; //storing the searcher for method calls before onCreateDialog, like addSeekBar
        Bundle args = new Bundle();
        args.putInt(CONDITIONS_NAME, 0);
        args.putInt(DISTANCE_NAME, 1000000);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
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

        for (FilterDescription filter : futureFilters) {
            filter.create();
        }
        for (int i = 0; i < filterViews.size(); i++) {
            View v = filterViews.get(i);
            layout.addView(v);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        ScrollView scrollView = new ScrollView(activity);
        scrollView.addView(layout);
        builder.setTitle(getString(R.string.filter_results)).setView(scrollView)
                .setPositiveButton(getString(R.string.search), (dialog, which) -> {
                    for (FilterDescription filter : futureFilters) {
                        SeekBarDescription seekBarDescription = (SeekBarDescription)filter;
                        if(filter.attribute != null) {
                            searcher.addNumericRefinement(new NumericRefinement(seekBarDescription.attribute, NumericRefinement.OPERATOR_GE, seekBarDescription.value));
                        } else if (filter.name.equals(DISTANCE_NAME)) {
                            double[] position = LocalUserProfile.getInstance().getCoordinates();
                            Query query = searcher.getQuery().setAroundLatLng(new AbstractQuery.LatLng(position[0], position[1]));
                            if (filter.value < ((SeekBarDescription) filter).max) {
                                query.setAroundRadius((int) filter.value);
                            } else {
                                query.setAroundRadius(Query.RADIUS_ALL);
                            }
                        }
                        getArguments().putInt(seekBarDescription.name, seekBarDescription.progressValue);
                    }
                    searcher.search();
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss());
        return builder.create();
    }

    /**
     * Add a SeekBar to the fragment, automatically fetching min and max values for its attribute.
     * This method <b>MUST</b> be called in the enclosing activity's <code>onCreate</code> to setup the required facets <b>before</b> the fragment is created.
     *
     * @param name  the attribute this SeekBar will filter on.
     * @param steps the amount of steps between min and max.
     * @return the fragment to allow chaining calls.
     */
    public FilterResultsFragment addSeekBar(final String name, final Double min, final Double max, final int steps) {
        futureFilters.add(new SeekBarDescription(null, name, min, max, steps, filterCount++));
        return this;
    }

    /**
     * Add a SeekBar to the fragment, naming the attribute differently in the UI.
     *
     * @param attribute the attribute this SeekBar will filter on.
     * @param name      the name to use when referring to the refined attribute.
     * @param min       the minimum value that the user can select.
     * @param max       the maximum value that the user can select.
     * @param steps     the amount of steps between min and max.
     * @return the fragment to allow chaining calls.
     */
    public FilterResultsFragment addSeekBar(final String attribute, final String name, final Double min, final Double max, final int steps) {
        futureFilters.add(new SeekBarDescription(attribute, name, min, max, steps, filterCount++));
        searcher.addFacet(attribute);
        return this;
    }

    /**
     * Add a CheckBox to the fragment, naming the attribute differently in the UI.
     *
     * @param attribute     the attribute this SeekBar will filter on.
     * @param name          the name to use when referring to the refined attribute.
     * @param checkedIsTrue if {@code true}, a checked box will refine on attribute:true, else on attribute:false.
     * @return the fragment to allow chaining calls.
     */
    public FilterResultsFragment addCheckBox(final String attribute, final String name, final boolean checkedIsTrue) {
        futureFilters.add(new CheckBoxDescription(attribute, name, checkedIsTrue, filterCount++));
        searcher.addFacet(attribute);
        return this;
    }

    private View getInflatedLayout(int layoutId) {
        Activity activity = getActivity();
        LayoutInflater inflater = activity.getLayoutInflater();
        return inflater.inflate(layoutId, null);
    }

    private void createFilter(final FilterDescription filter) {
        checkHasSearcher();

        final String attribute = filter.attribute;
        final String name = filter.name;
        boolean isSeekBar = filter instanceof SeekBarDescription;

        final View filterLayout = getInflatedLayout(isSeekBar ? R.layout.layout_seekbar : R.layout.layout_checkbox);

        if (isSeekBar) {
            final double minValue = ((SeekBarDescription) filter).min;
            final double maxValue = ((SeekBarDescription) filter).max;
            final int steps = ((SeekBarDescription) filter).steps;

            final TextView textView = filterLayout.findViewById(R.id.dialog_seekbar_text);
            final SeekBar seekBar = filterLayout.findViewById(R.id.dialog_seekbar_bar);
            seekBar.setMax(steps);
            seekBar.setProgress(getArguments().getInt(name));
            ((SeekBarDescription) filter).progressValue = getArguments().getInt(name);
            final double actualValue = getActualValue(seekBar, minValue, maxValue, steps);
            filter.value = actualValue;
            updateSeekBarText(textView, name, actualValue, minValue, maxValue);

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
                    final double actualValue = getActualValue(seekBar, minValue, maxValue, steps);
                    filter.value = actualValue;
                    updateSeekBarText(textView, name, actualValue, minValue, maxValue);
                    ((SeekBarDescription) filter).progressValue = seekBar.getProgress();
                }
            });

        } else {
            final TextView tv = filterLayout.findViewById(R.id.dialog_checkbox_text);
            final CheckBox checkBox = filterLayout.findViewById(R.id.dialog_checkbox_box);
            final Boolean currentFilter = searcher.getBooleanFilter(attribute);
            final FacetStat stats = searcher.getFacetStat(attribute);
            final boolean hasOnlyOneValue = stats != null && stats.min == stats.max;
            final boolean checkedIsTrue = ((CheckBoxDescription) filter).checkedIsTrue;

            if (currentFilter != null) { // If the attribute is currently filtered, show its state
                checkBox.setChecked(currentFilter);
            } else if (hasOnlyOneValue) { // If the attribute is not filtered and has only one value, disable its checkbox
                checkBox.setChecked(stats.min != 0); // If min=max=1, we check the box before disabling
                checkBox.setEnabled(false);
            }
            checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    searcher.addBooleanFilter(attribute, checkedIsTrue);
                } else {
                    searcher.removeBooleanFilter(attribute);
                }
            });
            tv.setText(name != null ? name : attribute);
        }
        filterViews.put(filter.position, filterLayout);
    }

    private double getActualValue(final SeekBar seekBar, final double minValue, final double maxValue, int steps) {
        int progress = seekBar.getProgress();
        return minValue + progress * (maxValue - minValue) / steps;
    }

    private void updateSeekBarText(final TextView textView, final String name, final double value, final double minValue, final double maxValue) {
        String text = "";

        if (name.equals(CONDITIONS_NAME)) {
            if(value == minValue) {
                text = getString(R.string.book_condition_any);
            } else {
                text = getString(R.string.conditions_filter, getResources().getString(Book.BookConditions.getStringId((int) value)).toLowerCase());
            }
        } else if (name.equals(DISTANCE_NAME)) {
            if(value == maxValue) {
                text = getString(R.string.no_distance_filter);
            } else {
                text = getString(R.string.maximum_distance, (int) value / 1000);
            }
        }
        textView.setText(text);
    }

    private void checkHasSearcher() {
        if (searcher == null) {
            throw new IllegalStateException("No searcher found");
        }
    }

    abstract class FilterDescription {
        final String attribute;
        final String name;
        final int position;
        double value;

        FilterDescription(String attribute, String name, int position) {
            this.attribute = attribute;
            this.name = name;
            this.position = position;
        }

        void create() {
            createFilter(this);
        }
    }

    private class SeekBarDescription extends FilterDescription {
        private Double min;
        private Double max;
        private final int steps;
        int progressValue;

        SeekBarDescription(String attribute, String name, Double min, Double max, int steps, int position) {
            super(attribute, name, position);
            this.min = min;
            this.max = max;
            this.steps = steps;
        }
    }

    private class CheckBoxDescription extends FilterDescription {
        private final boolean checkedIsTrue;

        CheckBoxDescription(String attribute, String name, boolean checkedIsTrue, int position) {
            super(attribute, name, position);
            this.checkedIsTrue = checkedIsTrue;
        }

    }
}