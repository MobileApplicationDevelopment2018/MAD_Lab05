package it.polito.mad.mad2018.data;

import android.content.res.Resources;
import android.support.annotation.NonNull;
import android.util.Base64;

import com.firebase.ui.database.FirebaseRecyclerOptions;
import com.google.android.gms.location.places.Place;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import it.polito.mad.mad2018.MAD2018Application;
import it.polito.mad.mad2018.R;
import it.polito.mad.mad2018.utils.Utilities;

public class UserProfile implements Serializable {

    public static final String PROFILE_INFO_KEY = "profile_info_key";

    static final String FIREBASE_USERS_KEY = "users";
    static final String FIREBASE_BOOKS_KEY = "books";
    static final String FIREBASE_OWNED_BOOKS_KEY = "ownedBooks";
    static final String FIREBASE_CONVERSATIONS_KEY = "conversations";
    static final String FIREBASE_ACTIVE_CONVERSATIONS_KEY = "active";
    static final String FIREBASE_ARCHIVED_CONVERSATIONS_KEY = "archived";
    static final String FIREBASE_PROFILE_KEY = "profile";
    static final String FIREBASE_STORAGE_IMAGE_NAME = "profile";
    static final int PROFILE_PICTURE_SIZE = 1024;
    static final int PROFILE_PICTURE_THUMBNAIL_SIZE = 64;
    static final int PROFILE_PICTURE_QUALITY = 50;
    private static final String FIREBASE_BORROWED_BOOKS_KEY = "borrowedBooks";
    private static final String FIREBASE_LENT_BOOKS_KEY = "lentBooks";
    private static final String FIREBASE_RATINGS_KEY = "ratings";
    private static final String FIREBASE_STORAGE_USERS_FOLDER = "users";
    final String uid;
    UserProfile.Data data;

    private transient ValueEventListener onProfileUpdatedFirebaseListener;
    private transient Set<OnProfileUpdatedListener> onProfileUpdatedListeners;
    private transient int onProfileUpdatedListenerCount;

    public UserProfile(@NonNull String uid, @NonNull Data data) {
        this.uid = uid;
        this.data = data;
        trimFields();

        this.onProfileUpdatedFirebaseListener = null;
        this.onProfileUpdatedListeners = new HashSet<>();
        this.onProfileUpdatedListenerCount = 0;
    }

    public static ValueEventListener setOnProfileLoadedListener(@NonNull String userId,
                                                                @NonNull ValueEventListener listener) {

        return FirebaseDatabase.getInstance().getReference()
                .child(FIREBASE_USERS_KEY)
                .child(userId)
                .addValueEventListener(listener);
    }

    public static void unsetOnProfileLoadedListener(@NonNull String userId,
                                                    @NonNull ValueEventListener listener) {

        FirebaseDatabase.getInstance().getReference()
                .child(FIREBASE_USERS_KEY)
                .child(userId)
                .removeEventListener(listener);
    }

    private static DatabaseReference getBooksReference(@NonNull String userId, @NonNull String booksKey) {
        return FirebaseDatabase.getInstance().getReference()
                .child(FIREBASE_USERS_KEY)
                .child(userId)
                .child(FIREBASE_BOOKS_KEY)
                .child(booksKey);
    }

    static DatabaseReference getOwnedBooksReference(@NonNull String userId) {
        return getBooksReference(userId, FIREBASE_OWNED_BOOKS_KEY);
    }

    static DatabaseReference getBorrowedBooksReference(@NonNull String userId) {
        return getBooksReference(userId, FIREBASE_BORROWED_BOOKS_KEY);
    }

    static DatabaseReference getLentBooksReference(@NonNull String userId) {
        return getBooksReference(userId, FIREBASE_LENT_BOOKS_KEY);
    }

    static StorageReference getStorageFolderReference(@NonNull String userId) {
        return FirebaseStorage.getInstance().getReference()
                .child(FIREBASE_STORAGE_USERS_FOLDER)
                .child(userId);
    }

    private void readObject(java.io.ObjectInputStream in)
            throws IOException, ClassNotFoundException {
        in.defaultReadObject();

        this.onProfileUpdatedFirebaseListener = null;
        this.onProfileUpdatedListeners = new HashSet<>();
        this.onProfileUpdatedListenerCount = 0;
    }

    public String getUserId() {
        return this.uid;
    }

    public String getEmail() {
        return this.data.profile.email;
    }

    public String getUsername() {
        return this.data.profile.username;
    }

    public String getLocation() {
        return this.data.profile.location.name;
    }

    JSONObject getLocationAlgolia() {
        return this.data.profile.location.toAlgoliaGeoLoc();
    }

    public String getLocationOrDefault() {
        return getLocation() == null
                ? MAD2018Application.getApplicationContextStatic().getString(R.string.default_city)
                : getLocation();

    }

    public double[] getCoordinates() {
        return new double[]{this.data.profile.location.latitude, this.data.profile.location.longitude};
    }

    public String getBiography() {
        return this.data.profile.biography;
    }

    public boolean hasProfilePicture() {
        return this.data.profile.hasProfilePicture;
    }

    public long getProfilePictureLastModified() {
        return this.data.profile.profilePictureLastModified;
    }

    public Object getProfilePictureReference() {
        return this.hasProfilePicture()
                ? this.getProfilePictureReferenceFirebase()
                : null;
    }

    public byte[] getProfilePictureThumbnail() {
        return this.data.profile.profilePictureThumbnail == null
                ? null
                : Base64.decode(this.data.profile.profilePictureThumbnail, Base64.DEFAULT);
    }

    private StorageReference getProfilePictureReferenceFirebase() {
        return UserProfile.getStorageFolderReference(this.uid)
                .child(FIREBASE_STORAGE_IMAGE_NAME);
    }

    public float getRating() {
        return (this.data.statistics.ratingCount == 0) ? 0
                : this.data.statistics.ratingTotal / this.data.statistics.ratingCount;
    }

    public int getOwnedBooksCount() {
        return this.data.books.ownedBooks.size();
    }

    public int getLentBooksCount() {
        return this.data.statistics.lentBooks;
    }

    public int getBorrowedBooksCount() {
        return this.data.statistics.borrowedBooks;
    }

    public int getToBeReturnedBooksCount() {
        return this.data.statistics.toBeReturnedBooks;
    }

    public FirebaseRecyclerOptions<Rating> getRatingsReferences() {
        return new FirebaseRecyclerOptions.Builder<Rating>()
                .setQuery(
                        FirebaseDatabase.getInstance().getReference()
                                .child(FIREBASE_USERS_KEY)
                                .child(getUserId())
                                .child(FIREBASE_RATINGS_KEY),
                        Rating.class)
                .build();
    }

    public void addOnProfileUpdatedListener() {
        this.addOnProfileUpdatedListener(null);
    }

    public void addOnProfileUpdatedListener(OnProfileUpdatedListener listener) {

        if (onProfileUpdatedListenerCount == 0) {
            onProfileUpdatedFirebaseListener = new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    UserProfile.Data data = dataSnapshot.getValue(UserProfile.Data.class);
                    if (data != null) {
                        UserProfile.this.data = data;
                        for (OnProfileUpdatedListener listener : onProfileUpdatedListeners) {
                            listener.onProfileUpdated(UserProfile.this);
                        }
                    }
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {
                    /* Do nothing */
                }
            };

            FirebaseDatabase.getInstance().getReference()
                    .child(FIREBASE_USERS_KEY)
                    .child(this.getUserId())
                    .addValueEventListener(onProfileUpdatedFirebaseListener);
        }

        if (listener != null) {
            onProfileUpdatedListeners.add(listener);
        }

        onProfileUpdatedListenerCount++;
    }

    public void removeOnProfileUpdatedListener() {
        this.removeOnProfileUpdatedListener(null);
    }

    public void removeOnProfileUpdatedListener(OnProfileUpdatedListener listener) {
        onProfileUpdatedListenerCount--;

        if (listener != null) {
            onProfileUpdatedListeners.remove(listener);
        }

        if (onProfileUpdatedListenerCount == 0) {
            FirebaseDatabase.getInstance().getReference()
                    .child(FIREBASE_USERS_KEY)
                    .child(this.getUserId())
                    .removeEventListener(onProfileUpdatedFirebaseListener);
            onProfileUpdatedFirebaseListener = null;
        }
    }

    void trimFields() {
        Resources resources = MAD2018Application.getApplicationContextStatic().getResources();
        this.data.profile.username = Utilities.trimString(this.data.profile.username, resources.getInteger(R.integer.max_length_username));
        this.data.profile.biography = Utilities.trimString(this.data.profile.biography, resources.getInteger(R.integer.max_length_biography));
    }

    public interface OnProfileUpdatedListener {
        void onProfileUpdated(@NonNull UserProfile profile);
    }

    /* Fields need to be public to enable Firebase to access them */
    @SuppressWarnings({"WeakerAccess", "CanBeFinal", "unused"})
    public static class Data implements Serializable {

        public Profile profile;
        public Statistics statistics;
        public Books books;
        public Conversations conversations;

        public Data() {
            this.profile = new Profile();
            this.statistics = new Statistics();
            this.books = new Books();
            this.conversations = new Conversations();
        }

        public Data(@NonNull Data other) {
            this.profile = new Profile(other.profile);
            this.statistics = new Statistics(other.statistics);
            this.books = new Books(other.books);
            this.conversations = new Conversations(other.conversations);
        }

        protected static class Profile implements Serializable {

            public String email;
            public String username;
            public Location location;
            public String biography;
            public boolean hasProfilePicture;
            public long profilePictureLastModified;
            public String profilePictureThumbnail;

            public Profile() {
                this.email = null;
                this.username = null;
                this.location = new Location();
                this.biography = null;
                this.hasProfilePicture = false;
                this.profilePictureLastModified = 0;
                this.profilePictureThumbnail = null;
            }

            public Profile(@NonNull Profile other) {
                this.email = other.email;
                this.username = other.username;
                this.location = new Location(other.location);
                this.biography = other.biography;
                this.hasProfilePicture = other.hasProfilePicture;
                this.profilePictureLastModified = other.profilePictureLastModified;
                this.profilePictureThumbnail = other.profilePictureThumbnail;
            }
        }

        protected static class Statistics implements Serializable {
            public float ratingTotal;
            public float ratingCount;
            public int lentBooks;
            public int borrowedBooks;
            public int toBeReturnedBooks;

            public Statistics() {
                this.ratingTotal = 0;
                this.ratingCount = 0;
                this.lentBooks = 0;
                this.borrowedBooks = 0;
                this.toBeReturnedBooks = 0;
            }

            public Statistics(@NonNull Statistics other) {
                this.ratingTotal = other.ratingTotal;
                this.ratingCount = other.ratingCount;
                this.lentBooks = other.lentBooks;
                this.borrowedBooks = other.borrowedBooks;
                this.toBeReturnedBooks = other.toBeReturnedBooks;
            }
        }

        protected static class Books implements Serializable {
            public Map<String, Boolean> ownedBooks;

            public Books() {
                this.ownedBooks = new HashMap<>();
            }

            public Books(@NonNull Books other) {
                this.ownedBooks = other.ownedBooks;
            }
        }

        protected static class Conversations implements Serializable {
            public Map<String, Conversation> active;
            public Map<String, Conversation> archived;

            public Conversations() {
                this.active = new HashMap<>();
                this.archived = new HashMap<>();
            }

            public Conversations(@NonNull Conversations other) {
                this.active = other.active;
                this.archived = other.archived;
            }

            protected static class Conversation implements Serializable {
                public String bookId;
                public long timestamp;

                public Conversation() {
                    this(null);
                }

                public Conversation(String bookId) {
                    this.bookId = bookId;
                    this.timestamp = 0;
                }
            }
        }

        protected static class Location implements Serializable {
            public String name;
            public double latitude;
            public double longitude;

            public Location() { /* Required by Firebase */ }

            public Location(Location other) {
                this.name = other.name;
                this.latitude = other.latitude;
                this.longitude = other.longitude;
            }

            public Location(@NonNull Place place) {
                this.name = place.getName().toString();
                this.latitude = place.getLatLng().latitude;
                this.longitude = place.getLatLng().longitude;
            }

            @Override
            public boolean equals(Object other) {
                if (this == other) {
                    return true;
                }

                if (!(other instanceof Location)) {
                    return false;
                }

                Location otherL = (Location) other;
                return this.name.equals(otherL.name) &&
                        Double.compare(this.latitude, otherL.latitude) == 0 &&
                        Double.compare(this.longitude, otherL.longitude) == 0;
            }

            private JSONObject toAlgoliaGeoLoc() {
                try {
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("lat", this.latitude);
                    jsonObject.put("lon", this.longitude);
                    return jsonObject;
                } catch (JSONException e) {
                    return null;
                }

            }
        }
    }
}
