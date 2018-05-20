package it.polito.mad.mad2018.library;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;

import com.firebase.ui.database.FirebaseRecyclerAdapter;
import com.firebase.ui.database.FirebaseRecyclerOptions;

import it.polito.mad.mad2018.R;
import it.polito.mad.mad2018.data.Book;
import it.polito.mad.mad2018.data.LocalUserProfile;
import it.polito.mad.mad2018.data.UserProfile;

public class MyBooksFragment extends Fragment {

    private UserProfile profile;
    private FirebaseRecyclerAdapter<Book, BookAdapter.BookHolder> adapter;
    private BookAdapter.OnItemCountChangedListener onItemCountChangedListener;

    public MyBooksFragment() { /* Required empty public constructor */ }

    public static MyBooksFragment newInstance() {
        return MyBooksFragment.newInstance(LocalUserProfile.getInstance());
    }

    public static MyBooksFragment newInstance(@NonNull UserProfile profile) {
        MyBooksFragment fragment = new MyBooksFragment();
        Bundle args = new Bundle();
        args.putSerializable(UserProfile.PROFILE_INFO_KEY, profile);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_my_books, container, false);

        assert getArguments() != null;
        profile = (UserProfile) getArguments().getSerializable(UserProfile.PROFILE_INFO_KEY);
        assert profile != null;

        final FloatingActionButton floatingActionButton = view.findViewById(R.id.fmb_add_book);
        floatingActionButton.setVisibility(profile instanceof LocalUserProfile ? View.VISIBLE : View.GONE);
        floatingActionButton.setOnClickListener(v -> {
            Intent toAddBook = new Intent(getActivity(), AddBookActivity.class);
            startActivity(toAddBook);
        });

        View noBooksView = view.findViewById(R.id.fmb_no_books);
        RecyclerView recyclerView = view.findViewById(R.id.fmb_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        onItemCountChangedListener = (count) -> {
            noBooksView.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
            recyclerView.setVisibility(count == 0 ? View.GONE : View.VISIBLE);
        };

        FirebaseRecyclerOptions<Book> options = Book.getOwnedBooksReference(profile.getUserId());
        adapter = new BookAdapter(options, (v, model) -> {
            Intent toBookInfo = new Intent(getActivity(), BookInfoActivity.class);
            toBookInfo.putExtra(Book.BOOK_KEY, model);
            toBookInfo.putExtra(BookInfoFragment.BOOK_SHOW_OWNER_KEY, false);
            toBookInfo.putExtra(BookInfoFragment.BOOK_DELETABLE_KEY, profile instanceof LocalUserProfile);
            startActivity(toBookInfo);
        }, onItemCountChangedListener);
        recyclerView.setAdapter(adapter);

        return view;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.clear();
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        assert getActivity() != null;
        getActivity().setTitle(profile instanceof LocalUserProfile
                ? getString(R.string.my_library)
                : getString(R.string.user_library, profile.getUsername()));
    }

    @Override
    public void onStart() {
        super.onStart();
        adapter.startListening();
        onItemCountChangedListener.onCountChangedListener(profile.getOwnedBooksCount());
    }

    @Override
    public void onStop() {
        adapter.stopListening();
        super.onStop();
    }
}
