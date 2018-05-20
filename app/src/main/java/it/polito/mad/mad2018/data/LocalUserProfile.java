package it.polito.mad.mad2018.data;

import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.util.Base64;

import com.algolia.search.saas.CompletionHandler;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserInfo;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import it.polito.mad.mad2018.MAD2018Application;
import it.polito.mad.mad2018.R;
import it.polito.mad.mad2018.utils.PictureUtilities;
import it.polito.mad.mad2018.utils.Utilities;

public class LocalUserProfile extends UserProfile {

    private static LocalUserProfile localInstance;

    private boolean localImageToBeDeleted;
    private String localImagePath;

    private LocalUserProfile(@NonNull String uid, @NonNull Data data) {
        super(uid, data);
        this.localImageToBeDeleted = false;
        this.localImagePath = null;
    }

    public LocalUserProfile(@NonNull Data data) {
        this(getCurrentUserId(), data);
    }

    public LocalUserProfile(@NonNull LocalUserProfile other) {
        this(other.uid, new Data(other.data));
    }

    public LocalUserProfile(@NonNull FirebaseUser user) {
        this(user.getUid(), new Data());

        this.data.profile.email = user.getEmail();
        this.data.profile.username = user.getDisplayName();

        for (UserInfo profile : user.getProviderData()) {
            if (this.data.profile.username == null && profile.getDisplayName() != null) {
                this.data.profile.username = profile.getDisplayName();
            }
        }

        if (this.data.profile.username == null) {
            this.data.profile.username = getUsernameFromEmail(this.data.profile.email);

        }

        this.data.profile.location.latitude = 45.116177;
        this.data.profile.location.longitude = 7.742615;
        this.data.profile.location.name = MAD2018Application.getApplicationContextStatic()
                .getString(R.string.default_city_turin);
    }

    public static LocalUserProfile getInstance() {
        return localInstance;
    }

    public static void setInstance(LocalUserProfile localInstance) {
        if (LocalUserProfile.localInstance != null) {
            LocalUserProfile.localInstance.removeOnProfileUpdatedListener();
        }

        LocalUserProfile.localInstance = localInstance;

        if (LocalUserProfile.localInstance != null) {
            LocalUserProfile.localInstance.addOnProfileUpdatedListener();
        }
    }

    private static String getUsernameFromEmail(@NonNull String email) {
        return email.substring(0, email.indexOf('@'));
    }

    private static String getCurrentUserId() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        assert currentUser != null;
        return currentUser.getUid();
    }

    public static boolean isLocal(String uid) {
        return Utilities.equals(uid, LocalUserProfile.getCurrentUserId());
    }

    public static ValueEventListener setOnProfileLoadedListener(@NonNull ValueEventListener listener) {
        return setOnProfileLoadedListener(getCurrentUserId(), listener);
    }

    public static void unsetOnProfileLoadedListener(@NonNull ValueEventListener listener) {
        unsetOnProfileLoadedListener(getCurrentUserId(), listener);
    }

    private static DatabaseReference getConversationsReference() {
        return FirebaseDatabase.getInstance().getReference()
                .child(FIREBASE_USERS_KEY)
                .child(LocalUserProfile.getCurrentUserId())
                .child(FIREBASE_CONVERSATIONS_KEY);
    }

    static DatabaseReference getActiveConversationsReference() {
        return LocalUserProfile.getConversationsReference()
                .child(FIREBASE_ACTIVE_CONVERSATIONS_KEY);
    }

    static DatabaseReference getArchivedConversationsReference() {
        return LocalUserProfile.getConversationsReference()
                .child(FIREBASE_ARCHIVED_CONVERSATIONS_KEY);
    }

    public void setProfilePicture(String path, boolean toBeDeleted) {
        this.data.profile.hasProfilePicture = path != null;
        this.data.profile.profilePictureLastModified = System.currentTimeMillis();
        this.data.profile.profilePictureThumbnail = null;
        localImageToBeDeleted = toBeDeleted;
        localImagePath = path;
    }

    public void resetProfilePicture() {
        setProfilePicture(null, false);
    }

    public void update(@NonNull String username, @NonNull String biography) {
        this.data.profile.username = username;
        this.data.profile.biography = biography;
    }

    public void update(Place place) {
        this.data.profile.location = place == null
                ? new Data.Location()
                : new Data.Location(place);
    }

    @Override
    public Object getProfilePictureReference() {
        return this.localImagePath == null
                ? super.getProfilePictureReference()
                : this.getLocalImagePath();
    }

    public void setProfilePictureThumbnail(ByteArrayOutputStream thumbnail) {
        this.data.profile.profilePictureThumbnail =
                Base64.encodeToString(thumbnail.toByteArray(), Base64.DEFAULT);
    }

    private StorageReference getProfilePictureReferenceFirebase() {
        return LocalUserProfile.getStorageFolderReference(this.uid)
                .child(FIREBASE_STORAGE_IMAGE_NAME);
    }

    public String getLocalImagePath() {
        return this.localImagePath;
    }

    public boolean isLocalImageToBeDeleted() {
        return localImageToBeDeleted;
    }

    public boolean profileUpdated(LocalUserProfile other) {
        return !Utilities.equals(this.getEmail(), other.getEmail()) ||
                !Utilities.equals(this.getUsername(), other.getUsername()) ||
                !Utilities.equals(this.getLocation(), other.getLocation()) ||
                !Utilities.equalsNullOrWhiteSpace(this.getBiography(), other.getBiography()) ||
                imageUpdated(other);
    }

    public boolean imageUpdated(LocalUserProfile other) {
        return this.hasProfilePicture() != other.hasProfilePicture() ||
                !Utilities.equals(this.localImagePath, other.localImagePath);
    }

    public Task<Void> saveToFirebase() {
        this.trimFields();

        return FirebaseDatabase.getInstance().getReference()
                .child(FIREBASE_USERS_KEY)
                .child(this.uid)
                .child(FIREBASE_PROFILE_KEY)
                .setValue(this.data.profile);
    }

    public void deleteProfilePictureFromFirebase() {
        getProfilePictureReferenceFirebase().delete();
    }

    public AsyncTask<Void, Void, PictureUtilities.CompressedImage> processProfilePictureAsync(
            @NonNull PictureUtilities.CompressImageAsync.OnCompleteListener onCompleteListener) {

        return new PictureUtilities.CompressImageAsync(
                localImagePath, PROFILE_PICTURE_SIZE, PROFILE_PICTURE_THUMBNAIL_SIZE,
                PROFILE_PICTURE_QUALITY, onCompleteListener)
                .execute();
    }

    public Task<?> uploadProfilePictureToFirebase(@NonNull ByteArrayOutputStream picture) {
        StorageMetadata metadata = new StorageMetadata.Builder()
                .setContentType(PictureUtilities.IMAGE_CONTENT_TYPE_UPLOAD)
                .build();

        return getProfilePictureReferenceFirebase()
                .putBytes(picture.toByteArray(), metadata);
    }

    public void postCommit() {
        this.localImageToBeDeleted = false;
        this.localImagePath = null;
    }

    public Task<?> addBook(String bookId) {
        this.data.books.ownedBooks.put(bookId, true);
        return FirebaseDatabase.getInstance().getReference()
                .child(FIREBASE_USERS_KEY)
                .child(getCurrentUserId())
                .child(FIREBASE_BOOKS_KEY)
                .child(FIREBASE_OWNED_BOOKS_KEY)
                .child(bookId)
                .setValue(true);
    }

    public void removeBook(String bookId) {
        this.data.books.ownedBooks.remove(bookId);
        FirebaseDatabase.getInstance().getReference()
                .child(FIREBASE_USERS_KEY)
                .child(getCurrentUserId())
                .child(FIREBASE_BOOKS_KEY)
                .child(FIREBASE_OWNED_BOOKS_KEY)
                .child(bookId)
                .removeValue();
    }

    public void updateAlgoliaGeoLoc(LocalUserProfile other, @NonNull CompletionHandler completionHandler) {

        if ((other != null && Utilities.equals(this.data.profile.location, other.data.profile.location)) ||
                this.data.books.ownedBooks.size() == 0) {
            completionHandler.requestCompleted(null, null);
            return;
        }

        JSONObject geoloc = this.getLocationAlgolia();
        List<JSONObject> bookUpdates = new ArrayList<>();

        for (String bookId : this.data.books.ownedBooks.keySet()) {
            try {
                bookUpdates.add(new JSONObject()
                        .put(Book.ALGOLIA_GEOLOC_KEY, geoloc)
                        .put(Book.ALGOLIA_BOOK_ID_KEY, bookId));
            } catch (JSONException e) { /* Do nothing */ }
        }

        OwnedBook.AlgoliaBookIndex.getInstance()
                .partialUpdateObjectsAsync(new JSONArray(bookUpdates), false, completionHandler);
    }

    Task<?> addConversation(@NonNull String conversationId, @NonNull String bookId) {
        Data.Conversations.Conversation conversation = new Data.Conversations.Conversation(bookId);
        this.data.conversations.active.put(conversationId, conversation);
        return LocalUserProfile.getActiveConversationsReference()
                .child(conversationId)
                .setValue(conversation);
    }

    Task<?> archiveConversation(@NonNull String conversationId) {

        Data.Conversations.Conversation conversation = this.data.conversations.active.remove(conversationId);
        if (conversation == null) {
            return null;
        }
        this.data.conversations.archived.put(conversationId, conversation);

        List<Task<?>> tasks = new ArrayList<>();
        tasks.add(LocalUserProfile.getActiveConversationsReference()
                .child(conversationId)
                .removeValue());
        tasks.add(LocalUserProfile.getArchivedConversationsReference()
                .child(conversationId)
                .setValue(conversation));
        return Tasks.whenAllSuccess(tasks);
    }

    public Task<?> deleteConversation(String conversationId) {
        this.data.conversations.archived.remove(conversationId);
        return LocalUserProfile.getArchivedConversationsReference()
                .child(conversationId)
                .removeValue();
    }

    public String findConversationByBookId(@NonNull String bookId) {
        for (Map.Entry<String, LocalUserProfile.Data.Conversations.Conversation> entry :
                this.data.conversations.active.entrySet()) {
            if (Utilities.equals(entry.getValue().bookId, bookId)) {
                return entry.getKey();
            }
        }
        return null;
    }
}
