package it.polito.mad.mad2018.library;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
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

public class LentBooksFragment extends Fragment {

    private FirebaseRecyclerAdapter<Book, BookAdapter.BookHolder> adapter;

    public LentBooksFragment() { /* Required empty public constructor */ }

    public static LentBooksFragment newInstance() {
        return new LentBooksFragment();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_lent_books, container, false);

        View noBooksView = view.findViewById(R.id.flb_no_books);
        RecyclerView recyclerView = view.findViewById(R.id.flb_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        FirebaseRecyclerOptions<Book> options = Book.getLentBooksReference(LocalUserProfile.getInstance().getUserId());
        adapter = new BookAdapter(options, (v, model) -> {
            Intent toBookInfo = new Intent(getActivity(), BookInfoActivity.class);
            toBookInfo.putExtra(Book.BOOK_KEY, model);
            toBookInfo.putExtra(BookInfoFragment.BOOK_SHOW_OWNER_KEY, false);
            toBookInfo.putExtra(BookInfoFragment.BOOK_DELETABLE_KEY, false);
            startActivity(toBookInfo);
        }, (count) -> {
            noBooksView.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
            recyclerView.setVisibility(count == 0 ? View.GONE : View.VISIBLE);
        });
        recyclerView.setAdapter(adapter);

        return view;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.clear();
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onStart() {
        super.onStart();
        adapter.startListening();
    }

    @Override
    public void onStop() {
        adapter.stopListening();
        super.onStop();
    }
}
