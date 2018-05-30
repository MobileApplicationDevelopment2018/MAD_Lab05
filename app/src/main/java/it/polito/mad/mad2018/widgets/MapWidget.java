package it.polito.mad.mad2018.widgets;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.algolia.instantsearch.model.AlgoliaResultsListener;
import com.algolia.instantsearch.model.SearchResults;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import it.polito.mad.mad2018.data.Book;

public class MapWidget implements AlgoliaResultsListener {

    @NonNull
    private final List<JSONObject> hits;
    private GoogleMap googleMap;

    public MapWidget(@NonNull final SupportMapFragment mapFragment,
                     @NonNull OnBookSelectedListener onBookSelectedListener) {
        hits = new ArrayList<>();
        mapFragment.getMapAsync(map -> {
            this.googleMap = map;
            this.googleMap.setOnInfoWindowClickListener(marker ->
                    onBookSelectedListener.onBookSelected((String) marker.getTag()));
            updateMapMarkers();
        });
    }

    private static String getMarkerTag(@NonNull JSONObject jsonObject) {
        try {
            return jsonObject.getString(Book.ALGOLIA_BOOK_ID_KEY);
        } catch (JSONException e) {
            return null;
        }
    }

    private static MarkerOptions buildMarker(@NonNull JSONObject jsonObject) {
        final MarkerOptions marker = new MarkerOptions();

        try {
            marker.title(jsonObject.getString(Book.ALGOLIA_BOOK_TITLE_KEY));

            JSONObject geoloc = jsonObject.getJSONObject(Book.ALGOLIA_GEOLOC_KEY);
            final Double latitude = geoloc.getDouble(Book.ALGOLIA_GEOLOC_LAT_KEY);
            final Double longitude = geoloc.getDouble(Book.ALGOLIA_GEOLOC_LON_KEY);
            marker.position(new LatLng(latitude, longitude));

            return marker;

        } catch (JSONException e) {
            return null;
        }
    }

    @Override
    public void onResults(@NonNull SearchResults results, boolean isLoadingMore) {
        addHits(results, !isLoadingMore);
        if (googleMap != null) {
            googleMap.setOnMapLoadedCallback(this::updateMapMarkers);
        }
    }

    private void addHits(@Nullable SearchResults results, boolean isReplacing) {
        if (results == null) {
            if (isReplacing) {
                hits.clear();
            }
            return;
        }

        final JSONArray newHits = results.hits;
        if (isReplacing) {
            hits.clear();
        }
        for (int i = 0; i < newHits.length(); ++i) {
            final JSONObject hit = newHits.optJSONObject(i);
            if (hit != null) {
                hits.add(hit);
            }
        }
    }

    private void updateMapMarkers() {
        googleMap.clear();
        if (hits.isEmpty())
            return;

        LatLngBounds.Builder builder = new LatLngBounds.Builder();

        for (final JSONObject hit : hits) {
            String tag = getMarkerTag(hit);
            MarkerOptions markerOptions = buildMarker(hit);
            if (tag != null && markerOptions != null) {
                builder.include(markerOptions.getPosition());
                Marker marker = googleMap.addMarker(markerOptions);
                marker.setTag(tag);
            }
        }

        LatLngBounds bounds = builder.build();

        int padding = 25;
        CameraUpdate cu = CameraUpdateFactory.newLatLngBounds(bounds, padding);
        googleMap.animateCamera(cu);
    }

    public interface OnBookSelectedListener {
        void onBookSelected(String bookId);
    }
}