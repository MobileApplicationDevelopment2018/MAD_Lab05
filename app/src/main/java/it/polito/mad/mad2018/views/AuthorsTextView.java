package it.polito.mad.mad2018.views;

import android.content.Context;
import android.support.v7.widget.AppCompatTextView;
import android.util.AttributeSet;

import com.algolia.instantsearch.ui.views.AlgoliaHitView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import it.polito.mad.mad2018.R;

public class AuthorsTextView extends AppCompatTextView implements AlgoliaHitView {

    public AuthorsTextView(Context context) {
        super(context);
    }

    public AuthorsTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public AuthorsTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public void onUpdateView(JSONObject result) {

        try {
            JSONArray jsonArray = result.getJSONArray("authors");

            if (jsonArray != null && jsonArray.length() > 0) {
                StringBuilder authors = new StringBuilder();
                for (int i = 0; i < jsonArray.length(); i++) {
                    if (i > 0) {
                        authors.append(", ");
                    }
                    authors.append(jsonArray.getString(i));
                    setText(authors.toString());
                }
            }
            return;
        } catch (JSONException e) { /* Do nothing */ }

        setText(getResources().getString(R.string.unknown_authors));
    }
}
