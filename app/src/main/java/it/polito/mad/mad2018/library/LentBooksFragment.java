package it.polito.mad.mad2018.library;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import it.polito.mad.mad2018.R;

public class LentBooksFragment extends Fragment {

    public LentBooksFragment() { /* Required empty public constructor */ }

    public static LentBooksFragment newInstance() {
        return new LentBooksFragment();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_lent_books, container, false);
    }
}
