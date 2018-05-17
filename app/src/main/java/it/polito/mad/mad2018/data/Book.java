package it.polito.mad.mad2018.data;

import android.content.res.Resources;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.text.TextUtils;

import com.algolia.search.saas.AlgoliaException;
import com.algolia.search.saas.Client;
import com.algolia.search.saas.CompletionHandler;
import com.algolia.search.saas.Index;
import com.firebase.ui.database.FirebaseRecyclerOptions;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.api.services.books.model.Volume;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;
import com.google.gson.GsonBuilder;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.Serializable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import it.polito.mad.mad2018.MAD2018Application;
import it.polito.mad.mad2018.R;
import it.polito.mad.mad2018.utils.PictureUtilities;
import it.polito.mad.mad2018.utils.Utilities;

public class Book implements Serializable {

    public static final String BOOK_KEY = "book_key";
    public static final String BOOK_ID_KEY = "book_id_key";

    public static final String ALGOLIA_BOOK_ID_KEY = "objectID";
    public static final String ALGOLIA_OWNER_ID_KEY = "ownerID";
    public static final String ALGOLIA_BOOK_TITLE_KEY = "title";
    public static final String ALGOLIA_HAS_IMAGE_KEY = "hasImage";
    public static final String ALGOLIA_CONDITIONS_KEY = "bookConditions.value";
    public static final String ALGOLIA_GEOLOC_KEY = "_geoloc";
    public static final String ALGOLIA_GEOLOC_LAT_KEY = "lat";
    public static final String ALGOLIA_GEOLOC_LON_KEY = "lon";

    public static final int INITIAL_YEAR = 1900;
    public static final int BOOK_PICTURE_SIZE = 1024;
    public static final int BOOK_PICTURE_QUALITY = 50;
    public static final int BOOK_THUMBNAIL_SIZE = 256;

    private static final String FIREBASE_BOOKS_KEY = "books";
    private static final String FIREBASE_DELETED_BOOK_KEY = "deleted";
    private static final String FIREBASE_STORAGE_BOOKS_FOLDER = "books";
    private static final String FIREBASE_STORAGE_IMAGE_NAME = "picture";
    private static final String FIREBASE_STORAGE_THUMBNAIL_NAME = "thumbnail";

    private final String bookId;
    private final Book.Data data;

    public Book(@NonNull String bookId, @NonNull Data data) {
        this.bookId = bookId;
        this.data = data;
    }

    public Book(@NonNull String isbn, @NonNull Volume.VolumeInfo volumeInfo) {
        this.bookId = generateBookId();
        this.data = new Data();

        this.data.bookInfo.isbn = isbn;
        this.data.bookInfo.title = volumeInfo.getTitle();
        this.data.bookInfo.authors = volumeInfo.getAuthors();
        this.data.bookInfo.publisher = volumeInfo.getPublisher();

        if (volumeInfo.getLanguage() != null) {
            this.data.bookInfo.language = new Locale(volumeInfo.getLanguage())
                    .getDisplayLanguage(Locale.getDefault());
        }

        String year = volumeInfo.getPublishedDate();
        if (year != null && year.length() >= 4) {
            this.data.bookInfo.year = Integer.parseInt(year.substring(0, 4));
        }

        if (volumeInfo.getCategories() != null) {
            this.data.bookInfo.tags.addAll(volumeInfo.getCategories());
        }
    }

    public Book(String isbn, @NonNull String title, @NonNull List<String> authors, @NonNull String language,
                String publisher, int year, @NonNull BookConditions conditions, @NonNull List<String> tags,
                @NonNull Resources resources) {
        this.bookId = generateBookId();
        this.data = new Data();

        for (String author : authors) {
            if (!Utilities.isNullOrWhitespace(author)) {
                this.data.bookInfo.authors.add(Utilities.trimString(author, resources.getInteger(R.integer.max_length_author)));
            }
        }

        this.data.bookInfo.isbn = isbn;
        this.data.bookInfo.title = Utilities.trimString(title, resources.getInteger(R.integer.max_length_title));
        this.data.bookInfo.language = Utilities.trimString(language, resources.getInteger(R.integer.max_length_language));
        this.data.bookInfo.publisher = Utilities.trimString(publisher, resources.getInteger(R.integer.max_length_publisher));
        this.data.bookInfo.year = year;

        this.data.bookInfo.bookConditions = conditions;
        for (String tag : tags) {
            if (!Utilities.isNullOrWhitespace(tag)) {
                this.data.bookInfo.tags.add(Utilities.trimString(tag, resources.getInteger(R.integer.max_length_tag)));
            }
        }
    }

    public static ValueEventListener setOnBookLoadedListener(@NonNull String bookId,
                                                             @NonNull ValueEventListener listener) {

        return FirebaseDatabase.getInstance().getReference()
                .child(FIREBASE_BOOKS_KEY)
                .child(bookId)
                .addValueEventListener(listener);
    }

    public static void unsetOnBookLoadedListener(@NonNull String bookId,
                                                 @NonNull ValueEventListener listener) {

        FirebaseDatabase.getInstance().getReference()
                .child(FIREBASE_BOOKS_KEY)
                .child(bookId)
                .removeEventListener(listener);
    }

    public static FirebaseRecyclerOptions<Book> getBooksByUser(@NonNull String userId) {

        DatabaseReference keyQuery = UserProfile.getOwnedBooksReference(userId);
        DatabaseReference dataRef = FirebaseDatabase.getInstance().getReference()
                .child(FIREBASE_BOOKS_KEY);

        return new FirebaseRecyclerOptions.Builder<Book>()
                .setIndexedQuery(keyQuery, dataRef,
                        snapshot -> {
                            String bookId = snapshot.getKey();
                            Data data = snapshot.getValue(Data.class);
                            assert data != null;
                            return new Book(bookId, data);
                        })
                .build();
    }

    private static String generateBookId() {
        return FirebaseDatabase.getInstance().getReference()
                .child(FIREBASE_BOOKS_KEY).push().getKey();
    }

    public static StorageReference getBookPictureReference(@NonNull String ownerId,
                                                           @NonNull String bookId) {
        return UserProfile.getStorageFolderReference(ownerId)
                .child(FIREBASE_STORAGE_BOOKS_FOLDER)
                .child(bookId)
                .child(FIREBASE_STORAGE_IMAGE_NAME);
    }

    public static StorageReference getBookThumbnailReference(@NonNull String ownerId,
                                                             @NonNull String bookId) {
        return UserProfile.getStorageFolderReference(ownerId)
                .child(FIREBASE_STORAGE_BOOKS_FOLDER)
                .child(bookId)
                .child(FIREBASE_STORAGE_THUMBNAIL_NAME);
    }

    public String getBookId() {
        return this.bookId;
    }

    public String getIsbn() {
        return this.data.bookInfo.isbn;
    }

    public String getTitle() {
        return this.data.bookInfo.title;
    }

    public List<String> getAuthors() {
        return this.data.bookInfo.authors;
    }

    public String getAuthors(@NonNull String delimiter) {
        return TextUtils.join(delimiter, this.data.bookInfo.authors);
    }

    public String getLanguage() {
        return this.data.bookInfo.language;
    }

    public String getPublisher() {
        return this.data.bookInfo.publisher;
    }

    public int getYear() {
        return this.data.bookInfo.year;
    }

    public String getConditions() {
        return this.data.bookInfo.bookConditions.toString();
    }

    public List<String> getTags() {
        return new ArrayList<>(this.data.bookInfo.tags);
    }

    public String getOwnerId() {
        return this.data.uid;
    }

    public boolean hasImage() {
        return this.data.bookInfo.hasImage;
    }

    public void setHasImage(boolean hasImage) {
        this.data.bookInfo.hasImage = hasImage;
    }

    private StorageReference getBookPictureReference() {
        return Book.getBookPictureReference(getOwnerId(), getBookId());
    }

    public StorageReference getBookPictureReferenceOrNull() {
        return this.data.bookInfo.hasImage ? this.getBookPictureReference() : null;
    }

    private StorageReference getBookThumbnailReference() {
        return Book.getBookThumbnailReference(getOwnerId(), getBookId());
    }

    public StorageReference getBookThumbnailReferenceOrNull() {
        return this.data.bookInfo.hasImage ? this.getBookThumbnailReference() : null;
    }

    public Task<?> saveToFirebase(@NonNull LocalUserProfile owner) {

        this.data.uid = owner.getUserId();

        List<Task<?>> tasks = new ArrayList<>();

        tasks.add(FirebaseDatabase.getInstance().getReference()
                .child(FIREBASE_BOOKS_KEY)
                .child(bookId)
                .setValue(this.data));

        tasks.add(owner.addBook(this.bookId));

        return Tasks.whenAllSuccess(tasks);
    }

    public Task<?> savePictureToFirebase(@NonNull UserProfile owner,
                                         @NonNull ByteArrayOutputStream picture,
                                         @NonNull ByteArrayOutputStream thumbnail) {

        this.data.uid = owner.getUserId();

        StorageMetadata metadata = new StorageMetadata.Builder()
                .setContentType(PictureUtilities.IMAGE_CONTENT_TYPE_UPLOAD)
                .build();

        List<Task<?>> tasks = new ArrayList<>();
        tasks.add(getBookPictureReference().putBytes(picture.toByteArray(), metadata));
        tasks.add(getBookThumbnailReference().putBytes(thumbnail.toByteArray(), metadata));
        return Tasks.whenAllSuccess(tasks);
    }

    public void deleteFromFirebase(@NonNull LocalUserProfile owner) {

        this.data.deleted = true;
        FirebaseDatabase.getInstance().getReference()
                .child(FIREBASE_BOOKS_KEY)
                .child(bookId)
                .child(FIREBASE_DELETED_BOOK_KEY)
                .setValue(true);

        owner.removeBook(this.bookId);
    }

    public void saveToAlgolia(@NonNull UserProfile owner,
                              @NonNull CompletionHandler completionHandler) {

        this.data.uid = owner.getUserId();

        JSONObject object = this.data.bookInfo.toJSON(owner.getUserId(), owner.getLocationAlgolia());
        if (object != null) {
            AlgoliaBookIndex.getInstance()
                    .addObjectAsync(object, bookId, completionHandler);
        } else {
            completionHandler.requestCompleted(null, new AlgoliaException(null));
        }
    }

    public void deleteFromAlgolia(CompletionHandler completionHandler) {
        AlgoliaBookIndex.getInstance()
                .deleteObjectAsync(bookId, completionHandler);
    }

    static class AlgoliaBookIndex {
        private static Index instance = null;

        static Index getInstance() {
            if (instance == null) {
                Client client = new Client(Constants.ALGOLIA_APP_ID, Constants.ALGOLIA_ADD_REMOVE_BOOK_API_KEY);
                instance = client.getIndex(Constants.ALGOLIA_INDEX_NAME);
            }
            return instance;
        }
    }

    /* Fields need to be public to enable Firebase to access them */
    @SuppressWarnings({"WeakerAccess", "CanBeFinal"})
    public static class Data implements Serializable {
        public String uid;
        public boolean deleted;
        public BookInfo bookInfo;

        public Data() {
            this.uid = null;
            this.deleted = false;
            this.bookInfo = new BookInfo();
        }

        private static class BookInfo implements Serializable {
            public String isbn;
            public String title;
            public List<String> authors;
            public String language;
            public String publisher;
            public int year;
            public BookConditions bookConditions;
            public List<String> tags;
            public boolean hasImage;

            public BookInfo() {
                this.isbn = null;
                this.title = null;
                this.authors = new ArrayList<>();
                this.language = null;
                this.publisher = null;
                this.year = INITIAL_YEAR;
                this.bookConditions = new BookConditions();
                this.tags = new ArrayList<>();
                this.hasImage = false;
            }

            private JSONObject toJSON(@NonNull String ownerId, @NonNull JSONObject geoloc) {
                try {
                    JSONObject data = new JSONObject(new GsonBuilder().create().toJson(this));
                    data.put(ALGOLIA_OWNER_ID_KEY, ownerId);
                    data.put(ALGOLIA_GEOLOC_KEY, geoloc);
                    return data;
                } catch (JSONException e) {
                    return null;
                }
            }
        }
    }

    /* Fields need to be public to enable Firebase to access them */
    @SuppressWarnings({"WeakerAccess", "CanBeFinal"})
    public static final class BookConditions implements Serializable, Comparable<BookConditions> {
        private static final int MINT = 40;
        private static final int GOOD = 30;
        private static final int FAIR = 20;
        private static final int POOR = 10;

        public int value;

        private BookConditions() {
            this(MINT);
        }

        private BookConditions(@Conditions int value) {
            this.value = value;
        }

        @StringRes
        public static int getStringId(@Conditions int value) {
            switch (value) {
                case MINT:
                default:
                    return R.string.add_book_condition_mint;
                case GOOD:
                    return R.string.add_book_condition_good;
                case FAIR:
                    return R.string.add_book_condition_fair;
                case POOR:
                    return R.string.add_book_condition_poor;
            }
        }

        public static List<BookConditions> values() {
            return Arrays.asList(
                    new BookConditions(POOR),
                    new BookConditions(FAIR),
                    new BookConditions(GOOD),
                    new BookConditions(MINT)
            );
        }

        @Override
        public String toString() {
            return MAD2018Application.getApplicationContextStatic()
                    .getString(getStringId(this.value));
        }

        @Override
        public boolean equals(Object other) {
            return this == other ||
                    other instanceof BookConditions
                            && this.value == ((BookConditions) other).value;
        }

        @Override
        public int hashCode() {
            return Integer.valueOf(value).hashCode();
        }

        @Override
        public int compareTo(@NonNull BookConditions other) {
            return Integer.compare(this.value, other.value);
        }

        @Retention(RetentionPolicy.SOURCE)
        @IntDef({MINT, GOOD, FAIR, POOR})
        private @interface Conditions {
        }
    }
}
