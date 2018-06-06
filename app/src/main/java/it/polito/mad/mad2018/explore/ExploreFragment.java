package it.polito.mad.mad2018.explore;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.AppBarLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.algolia.instantsearch.helpers.InstantSearch;
import com.algolia.instantsearch.helpers.Searcher;
import com.algolia.instantsearch.model.NumericRefinement;
import com.algolia.instantsearch.ui.views.SearchBox;
import com.algolia.search.saas.AbstractQuery;
import com.algolia.search.saas.Query;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.SupportMapFragment;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import it.polito.mad.mad2018.R;
import it.polito.mad.mad2018.data.Book;
import it.polito.mad.mad2018.data.Constants;
import it.polito.mad.mad2018.data.LocalUserProfile;
import it.polito.mad.mad2018.library.BookInfoActivity;
import it.polito.mad.mad2018.widgets.MapWidget;

public class ExploreFragment extends Fragment implements FilterResultsFragment.OnDismissListener {

    private final static int LIST_ID = 0;
    private final static int MAP_ID = 1;

    private static final String CONDITIONS_FILTER_NAME = "conditions";
    private static final String DISTANCE_FILTER_NAME = "distance";
    private static final String AVAILABILITY_FILTER_NAME = "availability";
    private static final String FILTERS_KEY = "filters";
    private final static String SEARCH_QUERY_KEY = "searchQuery";
    private ArrayList<Filter> filters;
    private Searcher searcher;
    private FilterResultsFragment filterResultsFragment;
    private ViewPager pager;
    private AppBarLayout appBarLayout;
    private View algoliaLogoLayout;
    private GoogleApiClient mGoogleApiClient;
    private String searchQuery;
    private InstantSearch helper;

    public static ExploreFragment newInstance() {
        return new ExploreFragment();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        searcher = Searcher.create(Constants.ALGOLIA_APP_ID, Constants.ALGOLIA_SEARCH_API_KEY,
                Constants.ALGOLIA_INDEX_NAME);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setupGoogleAPI();

        if (savedInstanceState != null) {
            searchQuery = savedInstanceState.getString(SEARCH_QUERY_KEY);
            filters = savedInstanceState.getParcelableArrayList(FILTERS_KEY);
            assert filters != null;
            for (Filter filter : filters) {
                filter.searcher = searcher;
                filter.context = getContext();
            }
        } else {
            searchQuery = "";
            filters = new ArrayList<>(3);
            createFilters();
        }

        assert filters != null;
        for (Filter filter : filters) {
            filter.applyFilter();
        }
        filterResultsFragment = (FilterResultsFragment) getChildFragmentManager().findFragmentByTag(FilterResultsFragment.TAG);
        if (filterResultsFragment == null) {
            filterResultsFragment = FilterResultsFragment.newInstance(filters);
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        setHasOptionsMenu(true);

        View view = inflater.inflate(R.layout.fragment_explore, container, false);
        algoliaLogoLayout = inflater.inflate(R.layout.algolia_logo_layout, null);

        pager = view.findViewById(R.id.search_pager);
        SearchResultsPagerAdapter pagerAdapter = new SearchResultsPagerAdapter(getChildFragmentManager());
        pager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            }

            @Override
            public void onPageSelected(int position) {
                assert getActivity() != null;
                Toolbar toolbar = getActivity().findViewById(R.id.toolbar);
                MenuItem icon = toolbar.getMenu().findItem(R.id.menu_action_map);
                if (icon != null) {
                    icon.setIcon((pager.getCurrentItem() == MAP_ID) ?
                            R.drawable.ic_format_list_bulleted_white_24dp : R.drawable.ic_location_on_white_24dp);

                }
            }

            @Override
            public void onPageScrollStateChanged(int state) {
            }
        });
        pager.setAdapter(pagerAdapter);

        return view;
    }

    @Override
    public void onAttachFragment(Fragment childFragment) {
        super.onAttachFragment(childFragment);

        if (childFragment instanceof SearchResultsTextFragment) {
            SearchResultsTextFragment instance = (SearchResultsTextFragment) childFragment;
            instance.setSearcher(searcher);
        }

        if (childFragment instanceof SupportMapFragment) {
            SupportMapFragment instance = (SupportMapFragment) childFragment;
            MapWidget mapWidget = new MapWidget(instance, bookId -> {
                Intent toBookInfo = new Intent(getActivity(), BookInfoActivity.class);
                toBookInfo.putExtra(Book.BOOK_ID_KEY, bookId);
                startActivity(toBookInfo);
            });
            searcher.registerResultListener(mapWidget);
        }
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        assert getActivity() != null;
        getActivity().setTitle(R.string.explore);
    }

    @Override
    public void onStart() {
        super.onStart();

        if (mGoogleApiClient != null) {
            mGoogleApiClient.connect();
        }
    }

    @Override
    public void onStop() {
        super.onStop();

        if (mGoogleApiClient != null) {
            mGoogleApiClient.disconnect();
        }
    }

    @Override
    public void onDestroyView() {
        if (appBarLayout != null) {
            appBarLayout.removeView(algoliaLogoLayout);
        }
        searchQuery = searcher.getQuery().getQuery();
        super.onDestroyView();
    }

    @Override
    public void onDetach() {
        searcher.destroy();
        super.onDetach();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.clear();

        assert getActivity() != null;

        inflater.inflate(R.menu.menu_explore, menu);
        helper = new InstantSearch(searcher);
        helper.registerSearchView(getActivity(), menu, R.id.menu_action_search);
        helper.search(searchQuery);

        MenuItem itemSearch = menu.findItem(R.id.menu_action_search);
        ImageView algoliaLogo = algoliaLogoLayout.findViewById(R.id.algolia_logo);
        algoliaLogo.setOnClickListener(v -> itemSearch.expandActionView());

        final SearchBox searchBox = (SearchBox) itemSearch.getActionView();

        itemSearch.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                if (searchQuery != null) {
                    searchBox.post(() -> {
                        searchBox.setQuery(searchQuery, false);
                        helper.setSearchOnEmptyString(true);
                    });
                }
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                searchQuery = searchBox.getQuery().toString();
                helper.setSearchOnEmptyString(false);
                return true;
            }
        });

        appBarLayout = getActivity().findViewById(R.id.app_bar_layout);
        appBarLayout.addView(algoliaLogoLayout);

        if (getActivity() != null) {
            MenuItem icon = menu.findItem(R.id.menu_action_map);

            if (icon != null) {
                icon.setIcon((pager.getCurrentItem() == MAP_ID) ? R.drawable.ic_format_list_bulleted_white_24dp : R.drawable.ic_location_on_white_24dp);
            }
        }

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_action_filter:
                filterResultsFragment.show(getChildFragmentManager(), FilterResultsFragment.TAG);
                return true;
            case R.id.menu_action_map:
                if (pager.getCurrentItem() == 0) {
                    showMap();
                } else {
                    hideMap();
                }
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(SEARCH_QUERY_KEY, searchQuery);
        outState.putParcelableArrayList(FILTERS_KEY, filters);
    }

    public void onBackPressed() {
        int item = pager.getCurrentItem() == 0 ? MAP_ID : LIST_ID;
        pager.setCurrentItem(item);
    }

    private void setupGoogleAPI() {
        assert getContext() != null;
        mGoogleApiClient = new GoogleApiClient.Builder(getContext())
                .addApi(LocationServices.API)
                .build();
    }

    public int getCurrentDisplayedFragment() {
        return pager.getCurrentItem();
    }

    private void showMap() {
        pager.setCurrentItem(MAP_ID);
    }

    private void hideMap() {
        pager.setCurrentItem(LIST_ID);
    }

    @Override
    public void onDialogDismiss() {
        helper.search(searcher.getQuery().getQuery());
    }

    private void checkHasSearcher() {
        if (searcher == null) {
            throw new IllegalStateException("No searcher found");
        }
    }

    private void createFilters() {

        final List<Book.BookConditions> bc = Book.BookConditions.values();
        ConditionsFilter conditionsFilter = new ConditionsFilter(CONDITIONS_FILTER_NAME,
                bc.get(0).value, bc.get(bc.size() - 1).value, bc.size() - 1, 0, searcher, getContext());
        conditionsFilter.value = conditionsFilter.min;
        filters.add(conditionsFilter);

        DistanceFilter distanceFilter = new DistanceFilter(DISTANCE_FILTER_NAME, 0, 1000000, 50, 1, searcher, getContext());
        distanceFilter.value = distanceFilter.max;
        filters.add(distanceFilter);

        CheckBoxFilter availabilityFilter = new CheckBoxFilter(AVAILABILITY_FILTER_NAME, true, getString(R.string.filter_available_books), 2, searcher, getContext());
        availabilityFilter.value = false;
        filters.add(availabilityFilter);
    }

    private class SearchResultsPagerAdapter extends FragmentStatePagerAdapter {
        SearchResultsPagerAdapter(FragmentManager fragmentManager) {
            super(fragmentManager);
        }

        @Override
        public Fragment getItem(int position) {
            return (position == 0
                    ? SearchResultsTextFragment.newInstance()
                    : SupportMapFragment.newInstance());
        }

        @Override
        public int getCount() {
            return 2;
        }
    }

    abstract class Filter implements Serializable, Parcelable {

        final String attribute;
        final String name;
        final int position;
        int value;
        View filterLayout;
        Context context;
        Searcher searcher;

        Filter(@Nullable String attribute, String name, int position, Searcher searcher, Context context) {
            this.attribute = attribute;
            this.name = name;
            this.position = position;
            this.context = context;
            this.searcher = searcher;
            if (attribute != null) {
                searcher.addFacet(attribute);
            }
        }

        Filter(Parcel in) {
            attribute = in.readString();
            name = in.readString();
            position = in.readInt();
            value = in.readInt();
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            out.writeString(attribute);
            out.writeString(name);
            out.writeInt(position);
            out.writeInt(value);
        }

        void applyFilter() {
            checkHasSearcher();
        }

        abstract void updateValue();

        abstract void createView(Integer value, FragmentActivity activity);

        abstract int getFilterLayoutValue();

        View getInflatedLayout(int layoutId, LayoutInflater inflater) {
            return inflater.inflate(layoutId, null);
        }
    }

    abstract class SeekBarFilter extends Filter {
        final int min;
        final int max;
        final int steps;
        int seekBarValue;

        SeekBarFilter(String attribute, String name, int min, int max, int steps, int position, Searcher searcher, Context context) {
            super(attribute, name, position, searcher, context);
            this.min = min;
            this.max = max;
            this.steps = steps;
        }

        SeekBarFilter(Parcel in) {
            super(in);
            min = in.readInt();
            max = in.readInt();
            steps = in.readInt();
            seekBarValue = in.readInt();
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeInt(min);
            out.writeInt(max);
            out.writeInt(steps);
            out.writeInt(seekBarValue);
        }

        @Override
        void createView(Integer value, FragmentActivity activity) {
            filterLayout = getInflatedLayout(R.layout.layout_seekbar, activity.getLayoutInflater());

            final TextView textView = filterLayout.findViewById(R.id.dialog_seekbar_text);
            final SeekBar seekBar = filterLayout.findViewById(R.id.dialog_seekbar_bar);
            seekBar.setMax(steps);
            seekBar.setSaveEnabled(false);

            if (value == null) {
                seekBar.setProgress(getSeekBarValue(this.value));
            } else {
                seekBar.setProgress(value);
            }
            seekBarValue = seekBar.getProgress();
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
                    updateSeekBarText(textView);
                }
            });
        }

        @Override
        int getFilterLayoutValue() {
            return seekBarValue;
        }

        @Override
        void updateValue() {
            final SeekBar seekBar = filterLayout.findViewById(R.id.dialog_seekbar_bar);
            value = getActualValue(seekBar.getProgress());
        }

        int getActualValue(int seekBarValue) {
            return min + seekBarValue * (max - min) / steps;
        }

        private int getSeekBarValue(int filterValue) {
            return (filterValue - min) * steps / (max - min);
        }

        abstract void updateSeekBarText(final TextView textView);
    }

    class ConditionsFilter extends SeekBarFilter {

        public final Parcelable.Creator<ConditionsFilter> CREATOR = new Parcelable.Creator<ConditionsFilter>() {

            @Override
            public ConditionsFilter createFromParcel(Parcel in) {
                return new ConditionsFilter(in);
            }

            @Override
            public ConditionsFilter[] newArray(int size) {
                return new ConditionsFilter[size];
            }
        };

        ConditionsFilter(String name, int min, int max, int steps, int position, Searcher searcher, Context context) {
            super(Book.ALGOLIA_CONDITIONS_KEY, name, min, max, steps, position, searcher, context);
        }

        ConditionsFilter(Parcel in) {
            super(in);
        }

        @Override
        void applyFilter() {
            super.applyFilter();
            searcher.addNumericRefinement(new NumericRefinement(attribute, NumericRefinement.OPERATOR_GE, value));
        }

        @Override
        void updateSeekBarText(final TextView textView) {

            String text;
            int value = getActualValue(seekBarValue);
            if (value == min) {
                text = context.getString(R.string.book_condition_any);
            } else {
                text = context.getString(R.string.conditions_filter, context.getString(Book.BookConditions.getStringId(value)).toLowerCase());
            }
            textView.setText(text);
        }
    }

    class DistanceFilter extends SeekBarFilter {

        public final Parcelable.Creator<DistanceFilter> CREATOR = new Parcelable.Creator<DistanceFilter>() {

            @Override
            public DistanceFilter createFromParcel(Parcel in) {
                return new DistanceFilter(in);
            }

            @Override
            public DistanceFilter[] newArray(int size) {
                return new DistanceFilter[size];
            }
        };

        DistanceFilter(String name, int min, int max, int steps, int position, Searcher searcher, Context context) {
            super(null, name, min, max, steps, position, searcher, context);
        }

        DistanceFilter(Parcel in) {
            super(in);
        }

        @Override
        void applyFilter() {
            super.applyFilter();
            double[] position = LocalUserProfile.getInstance().getCoordinates();
            Query query = searcher.getQuery().setAroundLatLng(new AbstractQuery.LatLng(position[0], position[1]));
            if (value < max) {
                query.setAroundRadius(value == 0 ? 1 : value);
            } else {
                query.setAroundRadius(Query.RADIUS_ALL);
            }
        }

        @Override
        void updateSeekBarText(final TextView textView) {
            String text;
            int value = getActualValue(seekBarValue);
            if (value == max) {
                text = context.getString(R.string.no_distance_filter);
            } else {
                text = context.getString(R.string.maximum_distance, value / 1000);
            }
            textView.setText(text);
        }
    }

    class CheckBoxFilter extends Filter {
        public final Parcelable.Creator<CheckBoxFilter> CREATOR = new Parcelable.Creator<CheckBoxFilter>() {

            @Override
            public CheckBoxFilter createFromParcel(Parcel in) {
                return new CheckBoxFilter(in);
            }

            @Override
            public CheckBoxFilter[] newArray(int size) {
                return new CheckBoxFilter[size];
            }
        };
        final boolean checkedIsTrue;
        final String text;
        boolean value;

        CheckBoxFilter(String name, boolean checkedIsTrue, String text, int position, Searcher searcher, Context context) {
            super(Book.ALGOLIA_AVAILABLE_KEY, name, position, searcher, context);
            this.checkedIsTrue = checkedIsTrue;
            this.text = text;
        }

        CheckBoxFilter(Parcel in) {
            super(in);
            checkedIsTrue = in.readByte() != 0;
            text = in.readString();
            value = in.readByte() != 0;
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeByte((byte) (checkedIsTrue ? 1 : 0));
            out.writeString(text);
            out.writeByte((byte) (value ? 1 : 0));
        }

        @Override
        void updateValue() {
            final CheckBox checkBox = filterLayout.findViewById(R.id.dialog_checkbox_box);
            value = checkBox.isChecked();
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
        void createView(Integer value, FragmentActivity activity) {
            filterLayout = getInflatedLayout(R.layout.layout_checkbox, activity.getLayoutInflater());

            final CheckBox checkBox = filterLayout.findViewById(R.id.dialog_checkbox_box);
            final TextView tv = filterLayout.findViewById(R.id.dialog_checkbox_text);

            checkBox.setSaveEnabled(false);

            if (value == null) {
                checkBox.setChecked(this.value);
            } else {
                checkBox.setChecked(value != 0);
            }
            tv.setText(text);
        }

        @Override
        int getFilterLayoutValue() {
            final CheckBox checkBox = filterLayout.findViewById(R.id.dialog_checkbox_box);
            return checkBox.isChecked() ? 1 : 0;
        }
    }
}
