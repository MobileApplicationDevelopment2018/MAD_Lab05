package it.polito.mad.mad2018.data;

import android.content.res.Resources;
import android.support.annotation.NonNull;

import com.algolia.search.saas.AlgoliaException;
import com.algolia.search.saas.Client;
import com.algolia.search.saas.CompletionHandler;
import com.algolia.search.saas.Index;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.api.services.books.model.Volume;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.StorageMetadata;
import com.google.gson.GsonBuilder;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import it.polito.mad.mad2018.MAD2018Application;
import it.polito.mad.mad2018.R;
import it.polito.mad.mad2018.utils.PictureUtilities;
import it.polito.mad.mad2018.utils.Utilities;

public class OwnedBook extends Book {

    public static final int BOOK_PICTURE_SIZE = 1024;
    public static final int BOOK_PICTURE_QUALITY = 50;
    public static final int BOOK_THUMBNAIL_SIZE = 256;

    public OwnedBook(@NonNull Book book) {
        super(book.bookId, book.data);

        if (!book.isOwnedBook()) {
            throw new BookNotOwnedException();
        }
    }

    public OwnedBook(@NonNull String isbn, @NonNull Volume.VolumeInfo volumeInfo) {
        super(generateBookId(), new Book.Data());

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

    public OwnedBook(String isbn, @NonNull String title, @NonNull List<String> authors, @NonNull String language,
                     String publisher, int year, @NonNull Book.BookConditions conditions, @NonNull List<String> tags) {
        super(generateBookId(), new Book.Data());

        Resources resources = MAD2018Application.getApplicationContextStatic().getResources();
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

    private static String generateBookId() {
        return FirebaseDatabase.getInstance().getReference()
                .child(FIREBASE_BOOKS_KEY).push().getKey();
    }

    private static JSONObject toJSONAlgolia(@NonNull String ownerId, @NonNull Book.Data.BookInfo bookInfo,
                                            @NonNull JSONObject geoloc) {
        try {
            JSONObject data = new JSONObject(new GsonBuilder().create().toJson(bookInfo));
            data.put(ALGOLIA_OWNER_ID_KEY, ownerId);
            data.put(ALGOLIA_GEOLOC_KEY, geoloc);
            data.put(ALGOLIA_AVAILABLE_KEY, true);
            return data;
        } catch (JSONException e) {
            return null;
        }
    }

    public void setHasImage(boolean hasImage) {
        this.data.bookInfo.hasImage = hasImage;
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

    public Task<?> savePictureToFirebase(@NonNull LocalUserProfile owner,
                                         @NonNull ByteArrayOutputStream picture,
                                         @NonNull ByteArrayOutputStream thumbnail) {

        this.data.uid = owner.getUserId();

        StorageMetadata metadata = new StorageMetadata.Builder()
                .setContentType(PictureUtilities.IMAGE_CONTENT_TYPE_UPLOAD)
                .build();

        List<Task<?>> tasks = new ArrayList<>();
        tasks.add(this.getBookPictureReference().putBytes(picture.toByteArray(), metadata));
        tasks.add(this.getBookThumbnailReference().putBytes(thumbnail.toByteArray(), metadata));
        return Tasks.whenAllSuccess(tasks);
    }

    public void deleteFromFirebase(@NonNull LocalUserProfile owner) {
        if (!this.isDeletable()) {
            throw new ForbiddenActionException();
        }

        this.data.flags.deleted = true;
        FirebaseDatabase.getInstance().getReference()
                .child(FIREBASE_BOOKS_KEY)
                .child(bookId)
                .child(FIREBASE_FLAGS_KEY)
                .child(FIREBASE_DELETED_BOOK_KEY)
                .setValue(true);

        owner.removeBook(this.bookId);
    }

    public void saveToAlgolia(@NonNull UserProfile owner,
                              @NonNull CompletionHandler completionHandler) {

        this.data.uid = owner.getUserId();

        JSONObject object = OwnedBook.toJSONAlgolia(owner.getUserId(), this.data.bookInfo, owner.getLocationAlgolia());
        if (object != null) {
            AlgoliaBookIndex.getInstance()
                    .addObjectAsync(object, bookId, completionHandler);
        } else {
            completionHandler.requestCompleted(null, new AlgoliaException(null));
        }
    }

    public void deleteFromAlgolia(CompletionHandler completionHandler) {
        if (!this.isDeletable()) {
            throw new ForbiddenActionException();
        }

        AlgoliaBookIndex.getInstance()
                .deleteObjectAsync(bookId, completionHandler);
    }

    public void updateAlgoliaAvailability(boolean available, CompletionHandler completionHandler) {

        JSONObject bookUpdate;
        try {
            bookUpdate = new JSONObject().put(Book.ALGOLIA_AVAILABLE_KEY, available);
        } catch (JSONException e) {
            if (completionHandler != null) {
                completionHandler.requestCompleted(null, new AlgoliaException(null));
            }
            return;
        }

        AlgoliaBookIndex.getInstance()
                .partialUpdateObjectAsync(bookUpdate, this.getBookId(), false, completionHandler);
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

    private static class BookNotOwnedException extends RuntimeException {
    }

    private static class ForbiddenActionException extends RuntimeException {
    }
}
