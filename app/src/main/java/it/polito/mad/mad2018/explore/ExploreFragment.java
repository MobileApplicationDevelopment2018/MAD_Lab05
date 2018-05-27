package it.polito.mad.mad2018.explore;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.AppBarLayout;
import android.support.v4.app.Fragment;
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
import android.widget.ImageView;

import com.algolia.instantsearch.helpers.InstantSearch;
import com.algolia.instantsearch.helpers.Searcher;
import com.algolia.instantsearch.ui.views.SearchBox;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.SupportMapFragment;

import it.polito.mad.mad2018.R;
import it.polito.mad.mad2018.data.Book;
import it.polito.mad.mad2018.data.Constants;
import it.polito.mad.mad2018.library.BookInfoActivity;
import it.polito.mad.mad2018.widgets.MapWidget;

public class ExploreFragment extends Fragment {

    private final static int LIST_ID = 0;
    private final static int MAP_ID = 1;

    private final Searcher searcher;
    private FilterResultsFragment filterResultsFragment;

    private ViewPager pager;

    private AppBarLayout appBarLayout;
    private View algoliaLogoLayout;
    private GoogleApiClient mGoogleApiClient;

    private String searchQuery;
    private final static String SEARCH_QUERY_STRING = "searchQuery";

    public ExploreFragment() {
        searcher = Searcher.create(Constants.ALGOLIA_APP_ID, Constants.ALGOLIA_SEARCH_API_KEY,
                Constants.ALGOLIA_INDEX_NAME);
    }

    public static ExploreFragment newInstance() {
        return new ExploreFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            searchQuery = savedInstanceState.getString(SEARCH_QUERY_STRING);
        }
        setupGoogleAPI();
        filterResultsFragment = FilterResultsFragment.getInstance(searcher);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
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
                if (icon == null) {
                    return;
                }

                icon.setIcon((pager.getCurrentItem() == MAP_ID) ?
                        R.drawable.ic_format_list_bulleted_white_24dp : R.drawable.ic_location_on_white_24dp);
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
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        searcher.destroy();
        super.onDestroy();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.clear();

        assert getActivity() != null;

        inflater.inflate(R.menu.menu_explore, menu);
        InstantSearch helper = new InstantSearch(searcher);
        helper.registerSearchView(getActivity(), menu, R.id.menu_action_search);
        if (searchQuery != null) {
            helper.search(searchQuery);
        } else {
            helper.search();
        }

        MenuItem itemSearch = menu.findItem(R.id.menu_action_search);
        ImageView algoliaLogo = algoliaLogoLayout.findViewById(R.id.algolia_logo);
        algoliaLogo.setOnClickListener(v -> itemSearch.expandActionView());

        final SearchBox searchBox = (SearchBox) itemSearch.getActionView();
        itemSearch.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {

            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                if (searchQuery != null) {
                    searchBox.post(() -> searchBox.setQuery(searchQuery, false));
                }
                helper.setSearchOnEmptyString(true);
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
        outState.putString(SEARCH_QUERY_STRING, searchQuery);
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
}
