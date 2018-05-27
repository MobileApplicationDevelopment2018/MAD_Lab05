package it.polito.mad.mad2018.explore;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.algolia.instantsearch.helpers.Searcher;
import com.algolia.instantsearch.model.AlgoliaErrorListener;
import com.algolia.instantsearch.model.AlgoliaResultsListener;
import com.algolia.instantsearch.model.SearchResults;
import com.algolia.instantsearch.ui.views.Hits;
import com.algolia.search.saas.AlgoliaException;
import com.algolia.search.saas.Query;

import org.json.JSONException;

import it.polito.mad.mad2018.R;
import it.polito.mad.mad2018.data.Book;
import it.polito.mad.mad2018.library.BookInfoActivity;

public class SearchResultsTextFragment extends Fragment
        implements AlgoliaResultsListener, AlgoliaErrorListener {

    private Searcher searcher;

    public SearchResultsTextFragment() { /* Required empty public constructor */ }

    public static SearchResultsTextFragment newInstance() {
        return new SearchResultsTextFragment();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        super.onCreateView(inflater, container, savedInstanceState);
        return inflater.inflate(R.layout.search_results_text_layout, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (searcher != null) {
            Hits hits = view.findViewById(R.id.algolia_hits);
            setHitsOnClickListener(hits);
            hits.initWithSearcher(searcher);
            searcher.registerResultListener(hits);
            searcher.registerResultListener(this);
            searcher.registerErrorListener(this);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        if (searcher != null) {
            searcher.unregisterResultListener(this);
            searcher.unregisterErrorListener(this);
        }

    }

    public void setSearcher(@NonNull Searcher searcher) {
        this.searcher = searcher;
    }

    private void setHitsOnClickListener(Hits hits) {

        hits.setOnItemClickListener((recyclerView, position, v) -> {

            try {
                String bookId = hits.get(position).getString(Book.ALGOLIA_BOOK_ID_KEY);
                if (bookId != null) {
                    Intent toBookInfo = new Intent(getActivity(), BookInfoActivity.class);
                    toBookInfo.putExtra(Book.BOOK_ID_KEY, bookId);
                    startActivity(toBookInfo);
                    return;
                }
            } catch (JSONException e) { /* Do nothing */ }

            Toast.makeText(getContext(), R.string.error_occurred, Toast.LENGTH_LONG).show();
        });
    }

    @Override
    public void onResults(@NonNull SearchResults results, boolean isLoadingMore) {
        assert getView() != null;
        getView().findViewById(R.id.algolia_loading).setVisibility(View.GONE);
        getView().findViewById(R.id.algolia_error).setVisibility(View.GONE);
        getView().findViewById(R.id.algolia_no_results).setVisibility(
                results.nbHits == 0 ? View.VISIBLE : View.GONE);
        getView().findViewById(R.id.algolia_hits).setVisibility(
                results.nbHits == 0 ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onError(@NonNull Query query, @NonNull AlgoliaException error) {
        assert getView() != null;
        getView().findViewById(R.id.algolia_loading).setVisibility(View.GONE);
        getView().findViewById(R.id.algolia_no_results).setVisibility(View.GONE);
        getView().findViewById(R.id.algolia_hits).setVisibility(View.GONE);
        getView().findViewById(R.id.algolia_error).setVisibility(View.VISIBLE);
    }
}
