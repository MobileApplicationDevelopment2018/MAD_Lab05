package it.polito.mad.mad2018.chat;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

import it.polito.mad.mad2018.R;

public class MyChatsFragment extends Fragment {

    private TabLayout tabLayout;
    private AppBarLayout appBarLayout;

    public MyChatsFragment() { /* Required empty public constructor */ }

    public static MyChatsFragment newInstance() {
        return new MyChatsFragment();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_library, container, false);

        ViewPager viewPager = view.findViewById(R.id.viewpager);
        setupViewPager(viewPager);

        tabLayout = (TabLayout) inflater.inflate(R.layout.tab_layout, null);
        tabLayout.setupWithViewPager(viewPager);

        return view;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        assert getActivity() != null;

        getActivity().setTitle(R.string.chat);
        appBarLayout = getActivity().findViewById(R.id.app_bar_layout);
        appBarLayout.addView(tabLayout);
    }

    @Override
    public void onDestroyView() {
        if (appBarLayout != null) {
            appBarLayout.removeView(tabLayout);
        }
        super.onDestroyView();
    }

    private void setupViewPager(ViewPager viewPager) {
        MyChatsFragment.ViewPagerAdapter adapter = new MyChatsFragment.ViewPagerAdapter(getChildFragmentManager());
        adapter.addFragment(ActiveChatsFragment.newInstance(), getResources().getString(R.string.active_chats));
        adapter.addFragment(ArchivedChatsFragment.newInstance(), getResources().getString(R.string.archived_chats));
        viewPager.setAdapter(adapter);
    }

    private static class ViewPagerAdapter extends FragmentPagerAdapter {

        private final List<Fragment> mFragmentList = new ArrayList<>();
        private final List<String> mFragmentTitleList = new ArrayList<>();

        ViewPagerAdapter(FragmentManager manager) {
            super(manager);
        }

        @Override
        public Fragment getItem(int position) {
            return mFragmentList.get(position);
        }

        @Override
        public int getCount() {
            return mFragmentList.size();
        }

        void addFragment(Fragment fragment, String title) {
            mFragmentList.add(fragment);
            mFragmentTitleList.add(title);
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return mFragmentTitleList.get(position);
        }
    }
}
