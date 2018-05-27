package it.polito.mad.mad2018.data;

import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.text.TextUtils;

import com.firebase.ui.database.FirebaseRecyclerOptions;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.StorageReference;

import java.io.Serializable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import it.polito.mad.mad2018.MAD2018Application;
import it.polito.mad.mad2018.R;
import it.polito.mad.mad2018.utils.Utilities;

public class Book implements Serializable {

    public static final String BOOK_KEY = "book_key";
    public static final String BOOK_ID_KEY = "book_id_key";

    public static final String ALGOLIA_BOOK_ID_KEY = "objectID";
    public static final String ALGOLIA_OWNER_ID_KEY = "ownerID";
    public static final String ALGOLIA_BOOK_TITLE_KEY = "title";
    public static final String ALGOLIA_HAS_IMAGE_KEY = "hasImage";
    public static final String ALGOLIA_CONDITIONS_KEY = "bookConditions.value";
    public static final String ALGOLIA_AVAILABLE_KEY = "available";
    public static final String ALGOLIA_GEOLOC_KEY = "_geoloc";
    public static final String ALGOLIA_GEOLOC_LAT_KEY = "lat";
    public static final String ALGOLIA_GEOLOC_LON_KEY = "lon";

    public static final int INITIAL_YEAR = 1900;

    static final String FIREBASE_BOOKS_KEY = "books";
    static final String FIREBASE_FLAGS_KEY = "flags";
    static final String FIREBASE_DELETED_BOOK_KEY = "deleted";
    private static final String FIREBASE_STORAGE_BOOKS_FOLDER = "books";
    private static final String FIREBASE_STORAGE_IMAGE_NAME = "picture";
    private static final String FIREBASE_STORAGE_THUMBNAIL_NAME = "thumbnail";

    final String bookId;
    final Book.Data data;

    private transient ValueEventListener onBookFlagsUpdatedListener;

    public Book(@NonNull String bookId, @NonNull Data data) {
        this.bookId = bookId;
        this.data = data;
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

    private static FirebaseRecyclerOptions<Book> getBooksReference(@NonNull DatabaseReference keyQuery) {

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

    public static FirebaseRecyclerOptions<Book> getOwnedBooksReference(@NonNull String userId) {
        return getBooksReference(UserProfile.getOwnedBooksReference(userId));
    }

    public static FirebaseRecyclerOptions<Book> getBorrowedBooksReference(@NonNull String userId) {
        return getBooksReference(UserProfile.getBorrowedBooksReference(userId));
    }

    public static FirebaseRecyclerOptions<Book> getLentBooksReference(@NonNull String userId) {
        return getBooksReference(UserProfile.getLentBooksReference(userId));
    }

    private static StorageReference getBookPictureReference(@NonNull String ownerId,
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

    public boolean isOwnedBook() {
        return Utilities.equals(this.data.uid, LocalUserProfile.getInstance().getUserId());
    }

    public boolean hasImage() {
        return this.data.bookInfo.hasImage;
    }

    public boolean isAvailable() {
        return this.data.flags.available && !isDeleted();
    }

    public boolean isDeletable() {
        return this.isAvailable() && this.isOwnedBook();
    }

    private boolean isDeleted() {
        return this.data.flags.deleted;
    }

    StorageReference getBookPictureReference() {
        return Book.getBookPictureReference(getOwnerId(), getBookId());
    }

    public StorageReference getBookPictureReferenceOrNull() {
        return this.data.bookInfo.hasImage ? this.getBookPictureReference() : null;
    }

    StorageReference getBookThumbnailReference() {
        return Book.getBookThumbnailReference(getOwnerId(), getBookId());
    }

    public StorageReference getBookThumbnailReferenceOrNull() {
        return this.data.bookInfo.hasImage ? this.getBookThumbnailReference() : null;
    }

    public void startOnBookFlagsUpdatedListener(@NonNull OnBookFlagsUpdatedListener listener) {

        stopOnBookFlagsUpdatedListener();

        onBookFlagsUpdatedListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                Book.Data.Flags flags = dataSnapshot.getValue(Book.Data.Flags.class);
                if (flags != null) {
                    Book.this.data.flags = flags;
                    listener.onBookFlagsUpdated();
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                /* Do nothing */
            }
        };

        FirebaseDatabase.getInstance().getReference()
                .child(FIREBASE_BOOKS_KEY)
                .child(this.getBookId())
                .child(FIREBASE_FLAGS_KEY)
                .addValueEventListener(onBookFlagsUpdatedListener);
    }

    public void stopOnBookFlagsUpdatedListener() {

        if (onBookFlagsUpdatedListener != null) {
            FirebaseDatabase.getInstance().getReference()
                    .child(FIREBASE_BOOKS_KEY)
                    .child(this.getBookId())
                    .child(FIREBASE_FLAGS_KEY)
                    .removeEventListener(onBookFlagsUpdatedListener);
            onBookFlagsUpdatedListener = null;
        }
    }

    public interface OnBookFlagsUpdatedListener {
        void onBookFlagsUpdated();
    }

    /* Fields need to be public to enable Firebase to access them */
    @SuppressWarnings({"WeakerAccess", "CanBeFinal"})
    public static class Data implements Serializable {
        public String uid;
        public BookInfo bookInfo;
        public Flags flags;

        public Data() {
            this.uid = null;
            this.bookInfo = new BookInfo();
            this.flags = new Flags();
        }

        static class BookInfo implements Serializable {
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
        }

        static class Flags implements Serializable {
            public boolean available;
            public boolean deleted;

            public Flags() {
                this.available = true;
                this.deleted = false;
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

        public @Conditions
        int value;

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
